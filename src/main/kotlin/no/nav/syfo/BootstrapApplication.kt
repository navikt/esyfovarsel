package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigFactory
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.auth.setupLocalRoutesWithAuthentication
import no.nav.syfo.auth.setupRoutesWithAuthentication
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.consumer.dokarkiv.DokarkivConsumer
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederConsumer
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.pdfgen.PdfgenConsumer
import no.nav.syfo.consumer.pdl.LocalPdlConsumer
import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import no.nav.syfo.consumer.veiledertilgang.VeilederTilgangskontrollConsumer
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.grantAccessToIAMUsers
import no.nav.syfo.job.SendMerVeiledningVarslerJobb
import no.nav.syfo.job.sendNotificationsJob
import no.nav.syfo.kafka.common.launchKafkaListener
import no.nav.syfo.kafka.consumers.infotrygd.InfotrygdKafkaConsumer
import no.nav.syfo.kafka.consumers.testdata.reset.TestdataResetConsumer
import no.nav.syfo.kafka.consumers.utbetaling.UtbetalingKafkaConsumer
import no.nav.syfo.kafka.consumers.varselbus.VarselBusKafkaConsumer
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideMicrofrontendKafkaProducer
import no.nav.syfo.metrics.registerPrometheusApi
import no.nav.syfo.producer.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProdusent
import no.nav.syfo.service.*
import no.nav.syfo.service.microfrontend.MikrofrontendDialogmoteService
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.service.mikrofrontend.MikrofrontendAktivitetskravService
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
        sendNotificationsJob(env)
    } else {
        val env = getEnv()
        val server = embeddedServer(
            Netty,
            applicationEngineEnvironment {
                config = HoconApplicationConfig(ConfigFactory.load())
                database = Database(env.dbEnv)
                database.grantAccessToIAMUsers()

                val azureAdTokenConsumer = AzureAdTokenConsumer(env.authEnv)
                val pdlConsumer = getPdlConsumer(env.urlEnv, azureAdTokenConsumer)
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
                val journalpostdistribusjonConsumer = JournalpostdistribusjonConsumer(env.urlEnv, azureAdTokenConsumer)
                val dokarkivConsumer = DokarkivConsumer(env.urlEnv, azureAdTokenConsumer)
                val dokarkivService = DokarkivService(dokarkivConsumer)

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
                val dialogmoteInnkallingVarselService = DialogmoteInnkallingVarselService(
                    senderFacade,
                    env.urlEnv.dialogmoterUrl,
                    accessControlService,
                )

                val aktivitetspliktForhandsvarselVarselService = AktivitetspliktForhandsvarselVarselService(
                    senderFacade,
                    accessControlService,
                    env.urlEnv.urlAktivitetskravInfoPage,
                    env.toggleEnv.sendAktivitetspliktForhandsvarsel,
                )
                val oppfolgingsplanVarselService =
                    OppfolgingsplanVarselService(senderFacade, accessControlService, env.urlEnv.oppfolgingsplanerUrl)
                val sykepengerMaxDateService = SykepengerMaxDateService(database, pdlConsumer)
                val pdfgenConsumer = PdfgenConsumer(env.urlEnv, pdlConsumer, database)
                val merVeiledningVarselService = MerVeiledningVarselService(
                    senderFacade,
                    env.urlEnv,
                    pdfgenConsumer,
                    dokarkivService,
                    accessControlService,
                )
                val mikrofrontendDialogmoteService = MikrofrontendDialogmoteService(database)
                val mikrofrontendAktivitetskravService = MikrofrontendAktivitetskravService(database)
                val mikrofrontendService =
                    MikrofrontendService(
                        minSideMicrofrontendKafkaProducer,
                        mikrofrontendDialogmoteService,
                        mikrofrontendAktivitetskravService,
                        database,
                    )

                val varselBusService =
                    VarselBusService(
                        senderFacade,
                        motebehovVarselService,
                        oppfolgingsplanVarselService,
                        dialogmoteInnkallingVarselService,
                        aktivitetspliktForhandsvarselVarselService,
                        mikrofrontendService,
                    )

                val veilederTilgangskontrollConsumer =
                    VeilederTilgangskontrollConsumer(env.urlEnv, azureAdTokenConsumer)

                val testdataResetService = TestdataResetService(database, mikrofrontendService, senderFacade)

                connector {
                    port = env.appEnv.applicationPort
                }

                module {
                    state.running = true

                    serverModule(
                        env,
                        merVeiledningVarselService,
                        sykepengerMaxDateService,
                        sykmeldingService,
                        mikrofrontendService,
                        pdlConsumer,
                        veilederTilgangskontrollConsumer,
                    )

                    kafkaModule(
                        env,
                        varselBusService,
                        sykepengerMaxDateService,
                        testdataResetService,
                    )
                }
            },
        )

        Runtime.getRuntime().addShutdownHook(
            Thread {
                server.stop(10, 10, TimeUnit.SECONDS)
            },
        )

        server.start(wait = false)
    }
}

private fun getPdlConsumer(urlEnv: UrlEnv, azureADConsumer: AzureAdTokenConsumer): PdlConsumer {
    return when {
        isLocal() -> LocalPdlConsumer(urlEnv, azureADConsumer)
        else -> PdlConsumer(urlEnv, azureADConsumer)
    }
}

private fun getDkifConsumer(urlEnv: UrlEnv, azureADConsumer: AzureAdTokenConsumer): DkifConsumer {
    return when {
        isLocal() -> DkifConsumer(urlEnv, azureADConsumer)
        else -> DkifConsumer(urlEnv, azureADConsumer)
    }
}

fun Application.serverModule(
    env: Environment,
    merVeiledningVarselService: MerVeiledningVarselService,
    sykepengerMaxDateService: SykepengerMaxDateService,
    sykmeldingService: SykmeldingService,
    mikrofrontendService: MikrofrontendService,
    pdlConsumer: PdlConsumer,
    veilederTilgangskontrollConsumer: VeilederTilgangskontrollConsumer,
) {
    val merVeiledningVarselFinder = MerVeiledningVarselFinder(
        database,
        sykmeldingService,
        pdlConsumer,
    )

    val sendMerVeiledningVarslerJobb = SendMerVeiledningVarslerJobb(
        merVeiledningVarselFinder,
        merVeiledningVarselService,
    )

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
            sendMerVeiledningVarslerJobb,
            mikrofrontendService,
            sykepengerMaxDateService,
            veilederTilgangskontrollConsumer,
            env.authEnv,
        )
    }

    runningLocally {
        setupLocalRoutesWithAuthentication(
            sendMerVeiledningVarslerJobb,
            mikrofrontendService,
            sykepengerMaxDateService,
            veilederTilgangskontrollConsumer,
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
    sykepengerMaxDateService: SykepengerMaxDateService,
    testdataResetService: TestdataResetService,
) {
    runningRemotely {
        launch(backgroundTasksContext) {
            launchKafkaListener(
                state,
                InfotrygdKafkaConsumer(env, sykepengerMaxDateService),
            )
        }

        launch(backgroundTasksContext) {
            launchKafkaListener(
                state,
                UtbetalingKafkaConsumer(env, sykepengerMaxDateService),
            )
        }

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
