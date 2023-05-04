package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigFactory
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.config.HoconApplicationConfig
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
import no.nav.syfo.job.VarselSender
import no.nav.syfo.job.sendNotificationsJob
import no.nav.syfo.kafka.common.launchKafkaListener
import no.nav.syfo.kafka.consumers.infotrygd.InfotrygdKafkaConsumer
import no.nav.syfo.kafka.consumers.syketilfelle.SyketilfelleKafkaConsumer
import no.nav.syfo.kafka.consumers.utbetaling.UtbetalingKafkaConsumer
import no.nav.syfo.kafka.consumers.varselbus.VarselBusKafkaConsumer
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideMicrofrontendKafkaProducer
import no.nav.syfo.metrics.registerPrometheusApi
import no.nav.syfo.planner.AktivitetskravVarselPlanner
import no.nav.syfo.planner.MerVeiledningVarselPlanner
import no.nav.syfo.producer.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProdusent
import no.nav.syfo.service.*
import no.nav.syfo.syketilfelle.SyketilfellebitService
import no.nav.syfo.utils.LeaderElection
import no.nav.syfo.utils.RunOnElection

data class ApplicationState(var running: Boolean = false, var initialized: Boolean = false)

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
                    env.urlEnv.baseUrlDineSykmeldte
                )
                val journalpostdistribusjonConsumer = JournalpostdistribusjonConsumer(env.urlEnv, azureAdTokenConsumer)
                val dokarkivConsumer = DokarkivConsumer(env.urlEnv, azureAdTokenConsumer)
                val dokarkivService = DokarkivService(dokarkivConsumer)

                val brukernotifikasjonKafkaProducer = BrukernotifikasjonKafkaProducer(env)
                val dineSykmeldteHendelseKafkaProducer = DineSykmeldteHendelseKafkaProducer(env)
                val dittSykefravaerMeldingKafkaProducer = DittSykefravaerMeldingKafkaProducer(env)
                val minSideMicrofrontendKafkaProducer = MinSideMicrofrontendKafkaProducer(env)

                val accessControlService = AccessControlService(pdlConsumer, dkifConsumer)
                val fysiskBrevUtsendingService = FysiskBrevUtsendingService(journalpostdistribusjonConsumer)
                val sykmeldingService = SykmeldingService(sykmeldingerConsumer)
                val syketilfellebitService = SyketilfellebitService(database)
                val varselSendtService = VarselSendtService(database)

                val merVeiledningVarselPlanner =
                    MerVeiledningVarselPlanner(database, syketilfellebitService, varselSendtService)
                val aktivitetskravVarselPlanner =
                    AktivitetskravVarselPlanner(database, syketilfellebitService, sykmeldingService)
                val replanleggingService =
                    ReplanleggingService(database, merVeiledningVarselPlanner, aktivitetskravVarselPlanner)
                val brukernotifikasjonerService =
                    BrukernotifikasjonerService(brukernotifikasjonKafkaProducer, accessControlService)
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
                    env.urlEnv.dialogmoterUrl,
                    sykmeldingService,
                )
                val dialogmoteInnkallingVarselService = DialogmoteInnkallingVarselService(
                    senderFacade,
                    env.urlEnv.dialogmoterUrl,
                    accessControlService,
                )
                val oppfolgingsplanVarselService =
                    OppfolgingsplanVarselService(senderFacade, env.urlEnv.oppfolgingsplanerUrl)
                val sykepengerMaxDateService = SykepengerMaxDateService(database, pdlConsumer)
                val pdfgenConsumer = PdfgenConsumer(env.urlEnv, pdlConsumer, database)
                val merVeiledningVarselService = MerVeiledningVarselService(
                    senderFacade,
                    syketilfellebitService,
                    env.urlEnv,
                    pdfgenConsumer,
                    dokarkivService
                )
                val mikrofrontendService = MikrofrontendService(minSideMicrofrontendKafkaProducer, database)

                val varselBusService =
                    VarselBusService(
                        motebehovVarselService,
                        oppfolgingsplanVarselService,
                        dialogmoteInnkallingVarselService,
                        mikrofrontendService
                    )

                val veilederTilgangskontrollConsumer =
                    VeilederTilgangskontrollConsumer(env.urlEnv, azureAdTokenConsumer)

                connector {
                    port = env.appEnv.applicationPort
                }

                module {
                    state.running = true

                    serverModule(
                        env,
                        accessControlService,
                        replanleggingService,
                        brukernotifikasjonKafkaProducer,
                        dineSykmeldteHendelseKafkaProducer,
                        arbeidsgiverNotifikasjonService,
                        merVeiledningVarselService,
                        sykepengerMaxDateService,
                        sykmeldingService,
                        pdlConsumer,
                        veilederTilgangskontrollConsumer,
                    )

                    kafkaModule(
                        env,
                        accessControlService,
                        varselBusService,
                        aktivitetskravVarselPlanner,
                        merVeiledningVarselPlanner,
                        sykepengerMaxDateService,
                    )
                }
            }
        )

        Runtime.getRuntime().addShutdownHook(
            Thread {
                server.stop(10, 10, TimeUnit.SECONDS)
            }
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
    accessControlService: AccessControlService,
    replanleggingService: ReplanleggingService,
    brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
    dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    merVeiledningVarselService: MerVeiledningVarselService,
    sykepengerMaxDateService: SykepengerMaxDateService,
    sykmeldingService: SykmeldingService,
    pdlConsumer: PdlConsumer,
    veilederTilgangskontrollConsumer: VeilederTilgangskontrollConsumer,
) {

    val sendVarselService =
        SendVarselService(
            brukernotifikasjonKafkaProducer,
            dineSykmeldteHendelseKafkaProducer,
            accessControlService,
            env.urlEnv,
            arbeidsgiverNotifikasjonService,
            merVeiledningVarselService,
            sykmeldingService,
        )

    val merVeiledningVarselFinder = MerVeiledningVarselFinder(
        database,
        sykmeldingService,
        pdlConsumer,
    )

    val varselSender = VarselSender(
        database,
        sendVarselService,
        merVeiledningVarselFinder,
        env.toggleEnv,
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
            varselSender,
            replanleggingService,
            sykepengerMaxDateService,
            veilederTilgangskontrollConsumer,
            env.authEnv
        )
    }

    runningLocally {
        setupLocalRoutesWithAuthentication(
            varselSender,
            replanleggingService,
            sykepengerMaxDateService,
            veilederTilgangskontrollConsumer,
            env.authEnv
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
    accessControlService: AccessControlService,
    varselbusService: VarselBusService,
    aktivitetskravVarselPlanner: AktivitetskravVarselPlanner,
    merVeiledningVarselPlanner: MerVeiledningVarselPlanner,
    sykepengerMaxDateService: SykepengerMaxDateService,
) {
    runningRemotely {
        launch(backgroundTasksContext) {
            launchKafkaListener(
                state,
                SyketilfelleKafkaConsumer(env, accessControlService, database)
                    .addPlanner(merVeiledningVarselPlanner)
                    .addPlanner(aktivitetskravVarselPlanner)
            )
        }

        if (env.toggleEnv.toggleInfotrygdKafkaConsumer) {
            launch(backgroundTasksContext) {
                launchKafkaListener(
                    state,
                    InfotrygdKafkaConsumer(env, sykepengerMaxDateService)
                )
            }
        }

        if (env.toggleEnv.toggleUtbetalingKafkaConsumer) {
            launch(backgroundTasksContext) {
                launchKafkaListener(
                    state,
                    UtbetalingKafkaConsumer(env, sykepengerMaxDateService)
                )
            }
        }

        launch(backgroundTasksContext) {
            launchKafkaListener(
                state,
                VarselBusKafkaConsumer(env, varselbusService)
            )
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
