package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigFactory
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.install
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.auth.setupLocalRoutesWithAuthentication
import no.nav.syfo.auth.setupRoutesWithAuthentication
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederConsumer
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.pdl.PdlClient
import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.grantAccessToIAMUsers
import no.nav.syfo.job.ResendFailedVarslerJob
import no.nav.syfo.job.SendAktivitetspliktLetterToSentralPrintJob
import no.nav.syfo.job.closeExpiredMicrofrontendsJob
import no.nav.syfo.kafka.common.launchKafkaListener
import no.nav.syfo.kafka.consumers.testdata.reset.TestdataResetConsumer
import no.nav.syfo.kafka.consumers.varselbus.VarselBusKafkaConsumer
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideMicrofrontendKafkaProducer
import no.nav.syfo.metrics.registerPrometheusApi
import no.nav.syfo.producer.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProdusent
import no.nav.syfo.service.AccessControlService
import no.nav.syfo.service.AktivitetspliktForhandsvarselVarselService
import no.nav.syfo.service.ArbeidsgiverNotifikasjonService
import no.nav.syfo.service.ArbeidsuforhetForhandsvarselService
import no.nav.syfo.service.BrukernotifikasjonerService
import no.nav.syfo.service.DialogmoteInnkallingNarmesteLederVarselService
import no.nav.syfo.service.DialogmoteInnkallingSykmeldtVarselService
import no.nav.syfo.service.FriskmeldingTilArbeidsformidlingVedtakService
import no.nav.syfo.service.FysiskBrevUtsendingService
import no.nav.syfo.service.ManglendeMedvirkningVarselService
import no.nav.syfo.service.MerVeiledningVarselService
import no.nav.syfo.service.MotebehovVarselService
import no.nav.syfo.service.OppfolgingsplanVarselService
import no.nav.syfo.service.SenderFacade
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.service.TestdataResetService
import no.nav.syfo.service.VarselBusService
import no.nav.syfo.service.microfrontend.MikrofrontendAktivitetskravService
import no.nav.syfo.service.microfrontend.MikrofrontendDialogmoteService
import no.nav.syfo.service.microfrontend.MikrofrontendMerOppfolgingService
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.utils.LeaderElection
import no.nav.syfo.utils.RunOnElection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val state: ApplicationState = ApplicationState()
val backgroundTasksContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
lateinit var database: DatabaseInterface

fun main() {
    if (isJob()) {
        val env = getJobEnv()
        closeExpiredMicrofrontendsJob(env)
    } else {
        val env = getEnv()
        val server = embeddedServer(
            factory = Netty,
            environment = createApplicationEnvironment(env),
            configure = {
                connector {
                    port = env.appEnv.applicationPort
                }
                connectionGroupSize = 8
                workerGroupSize = 8
                callGroupSize = 16
            },
            module = setModule(env)
        )

        Runtime.getRuntime().addShutdownHook(
            Thread {
                server.stop(10, 10, TimeUnit.SECONDS)
            },
        )

        server.start(wait = false)
    }
}

fun createApplicationEnvironment(env: Environment): ApplicationEnvironment = applicationEnvironment {
    config = HoconApplicationConfig(ConfigFactory.load())
    database = Database(env.dbEnv)
    database.grantAccessToIAMUsers()
}

@Suppress("LongMethod")
fun setModule(env: Environment): Application.() -> Unit = {
    val azureAdTokenConsumer = AzureAdTokenConsumer(env.authEnv)
    val dkifConsumer = getDkifConsumer(env.urlEnv, azureAdTokenConsumer)
    val sykmeldingerConsumer = SykmeldingerConsumer(env.urlEnv, azureAdTokenConsumer)
    val narmesteLederConsumer = NarmesteLederConsumer(env.urlEnv, azureAdTokenConsumer)
    val narmesteLederService = NarmesteLederService(narmesteLederConsumer)
    val arbeidsgiverNotifikasjonProdusent =
        ArbeidsgiverNotifikasjonProdusent(env.urlEnv, azureAdTokenConsumer)
    val arbeidsgiverNotifikasjonService = ArbeidsgiverNotifikasjonService(
        arbeidsgiverNotifikasjonProdusent,
        narmesteLederService,
        env.urlEnv.baseUrlDineSykmeldte,
    )
    val pdlClient = PdlClient(env.urlEnv, azureAdTokenConsumer)
    val journalpostdistribusjonConsumer = JournalpostdistribusjonConsumer(env.urlEnv, azureAdTokenConsumer)

    val brukernotifikasjonKafkaProducer = BrukernotifikasjonKafkaProducer(env)
    val dineSykmeldteHendelseKafkaProducer = DineSykmeldteHendelseKafkaProducer(env)
    val dittSykefravaerMeldingKafkaProducer = DittSykefravaerMeldingKafkaProducer(env)
    val minSideMicrofrontendKafkaProducer = MinSideMicrofrontendKafkaProducer(env)

    val accessControlService = AccessControlService(dkifConsumer)
    val fysiskBrevUtsendingService = FysiskBrevUtsendingService(journalpostdistribusjonConsumer)
    val sykmeldingService = SykmeldingService(sykmeldingerConsumer)
    val brukernotifikasjonerService =
        BrukernotifikasjonerService(brukernotifikasjonKafkaProducer)
    val senderFacade = SenderFacade(
        dineSykmeldteHendelseKafkaProducer,
        dittSykefravaerMeldingKafkaProducer,
        brukernotifikasjonerService,
        arbeidsgiverNotifikasjonService,
        fysiskBrevUtsendingService,
        database,
    )
    val motebehovVarselService = MotebehovVarselService(
        senderFacade,
        accessControlService,
        sykmeldingService,
        env.urlEnv.dialogmoterUrl,
    )
    val dialogmoteInnkallingSykmeldtVarselService = DialogmoteInnkallingSykmeldtVarselService(
        senderFacade = senderFacade,
        dialogmoterUrl = env.urlEnv.dialogmoterUrl,
        accessControlService = accessControlService,
        database = database
    )
    val dialogmoteInnkallingNarmesteLederVarselService = DialogmoteInnkallingNarmesteLederVarselService(
        senderFacade = senderFacade,
        dialogmoterUrl = env.urlEnv.dialogmoterUrl,
        narmesteLederService = narmesteLederService,
        pdlClient = pdlClient,
    )

    val aktivitetspliktForhandsvarselVarselService = AktivitetspliktForhandsvarselVarselService(
        senderFacade,
        accessControlService,
        env.urlEnv.urlAktivitetskravInfoPage,
        env.toggleEnv.sendAktivitetspliktForhandsvarsel,
    )
    val arbeidsuforhetForhandsvarselService = ArbeidsuforhetForhandsvarselService(senderFacade)
    val friskmeldingTilArbeidsformidlingVedtakService = FriskmeldingTilArbeidsformidlingVedtakService(senderFacade)
    val manglendeMedvirkningVarselService = ManglendeMedvirkningVarselService(senderFacade)
    val oppfolgingsplanVarselService =
        OppfolgingsplanVarselService(
            senderFacade,
            accessControlService,
            env.urlEnv.oppfolgingsplanerUrl,
            narmesteLederService,
            pdlClient
        )
    val merVeiledningVarselService = MerVeiledningVarselService(
        senderFacade = senderFacade,
        env = env,
        accessControlService = accessControlService,
    )
    val mikrofrontendDialogmoteService = MikrofrontendDialogmoteService(database)
    val mikrofrontendAktivitetskravService = MikrofrontendAktivitetskravService(database)
    val mikrofrontendMerOppfolgingService = MikrofrontendMerOppfolgingService(database)
    val mikrofrontendService =
        MikrofrontendService(
            minSideMicrofrontendKafkaProducer = minSideMicrofrontendKafkaProducer,
            mikrofrontendDialogmoteService = mikrofrontendDialogmoteService,
            mikrofrontendAktivitetskravService = mikrofrontendAktivitetskravService,
            mikrofrontendMerOppfolgingService = mikrofrontendMerOppfolgingService,
            database = database,
        )

    val sendAktivitetspliktLetterToSentralPrintJob = SendAktivitetspliktLetterToSentralPrintJob(database, senderFacade)

    val resendFailedVarslerJob = ResendFailedVarslerJob(
        db = database,
        motebehovVarselService = motebehovVarselService,
        dialogmoteInnkallingSykmeldtVarselService = dialogmoteInnkallingSykmeldtVarselService,
        merVeiledningVarselService = merVeiledningVarselService,
        senderFacade = senderFacade
    )

    val varselBusService =
        VarselBusService(
            senderFacade,
            motebehovVarselService,
            oppfolgingsplanVarselService,
            dialogmoteInnkallingSykmeldtVarselService,
            dialogmoteInnkallingNarmesteLederVarselService,
            aktivitetspliktForhandsvarselVarselService,
            arbeidsuforhetForhandsvarselService,
            mikrofrontendService,
            friskmeldingTilArbeidsformidlingVedtakService,
            manglendeMedvirkningVarselService,
            merVeiledningVarselService
        )

    val testdataResetService = TestdataResetService(database, mikrofrontendService, senderFacade)

    state.running = true

    serverModule(
        env,
        mikrofrontendService,
        sendAktivitetspliktLetterToSentralPrintJob,
        resendFailedVarslerJob
    )

    kafkaModule(
        env,
        varselBusService,
        testdataResetService,
    )
}

private fun getDkifConsumer(urlEnv: UrlEnv, azureADConsumer: AzureAdTokenConsumer): DkifConsumer {
    return when {
        isLocal() -> DkifConsumer(urlEnv, azureADConsumer)
        else -> DkifConsumer(urlEnv, azureADConsumer)
    }
}

fun Application.serverModule(
    env: Environment,
    mikrofrontendService: MikrofrontendService,
    sendAktivitetspliktLetterToSentralPrintJob: SendAktivitetspliktLetterToSentralPrintJob,
    resendFailedVarslerJob: ResendFailedVarslerJob
) {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    val electionJobList = emptyList<RunOnElection>()
    val leaderElection = LeaderElection(electionJobList)

    launch(backgroundTasksContext) {
        while (state.running) {
            delay(300000)
            leaderElection.checkIfPodIsLeader()
        }
    }

    runningRemotely {
        setupRoutesWithAuthentication(
            mikrofrontendService,
            sendAktivitetspliktLetterToSentralPrintJob,
            resendFailedVarslerJob,
            env.authEnv,
        )
    }

    runningLocally {
        setupLocalRoutesWithAuthentication(
            mikrofrontendService,
            sendAktivitetspliktLetterToSentralPrintJob,
            resendFailedVarslerJob,
            env.authEnv,
        )
    }

    routing {
        registerPrometheusApi()
        registerNaisApi(state)
    }

    state.initialized = true
}

fun Application.kafkaModule(
    env: Environment,
    varselbusService: VarselBusService,
    testdataResetService: TestdataResetService,
) {
    runningRemotely {
        launch(backgroundTasksContext) {
            launchKafkaListener(
                state,
                VarselBusKafkaConsumer(env, varselbusService),
            )
        }

        if (env.isDevGcp()) {
            launch(backgroundTasksContext) {
                launchKafkaListener(
                    state,
                    TestdataResetConsumer(env, testdataResetService),
                )
            }
        }
    }
}

val Application.envKind
    get() = environment.config.property("ktor.environment").getString()

fun Application.runningRemotely(block: () -> Unit) {
    if (envKind == "remote") block()
}

fun Application.runningLocally(block: () -> Unit) {
    if (envKind == "local") block()
}
