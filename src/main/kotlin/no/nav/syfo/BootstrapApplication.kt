package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.jackson.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.auth.*
import no.nav.syfo.consumer.LocalPdlConsumer
import no.nav.syfo.consumer.LocalSyfosyketilfelleConsumer
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.consumer.arbeidsgiverNotifikasjonProdusent.ArbeidsgiverNotifikasjonProdusent
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederConsumer
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.syfomotebehov.SyfoMotebehovConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import no.nav.syfo.db.*
import no.nav.syfo.job.VarselSender
import no.nav.syfo.job.sendNotificationsJob
import no.nav.syfo.kafka.common.launchKafkaListener
import no.nav.syfo.kafka.consumers.kandidatliste.KandidatlisteDialogmoteKafkaConsumer
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.OppfolgingstilfelleKafkaConsumer
import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.SyketilfelleKafkaConsumer
import no.nav.syfo.kafka.consumers.varselbus.VarselBusKafkaConsumer
import no.nav.syfo.metrics.registerPrometheusApi
import no.nav.syfo.service.*
import no.nav.syfo.syketilfelle.SyketilfelleService
import no.nav.syfo.varsel.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
                database = initDb(env.dbEnv)

                val stsConsumer = getStsConsumer(env.urlEnv, env.authEnv)
                val azureAdTokenConsumer = AzureAdTokenConsumer(env.authEnv)

                val pdlConsumer = getPdlConsumer(env.urlEnv, azureAdTokenConsumer, stsConsumer)
                val dkifConsumer = getDkifConsumer(env.urlEnv, azureAdTokenConsumer, stsConsumer)
                val oppfolgingstilfelleConsumer = getSyfosyketilfelleConsumer(env.urlEnv, stsConsumer)
                val sykmeldingerConsumer = SykmeldingerConsumer(env.urlEnv, azureAdTokenConsumer)
                val narmesteLederConsumer = NarmesteLederConsumer(env.urlEnv, azureAdTokenConsumer)
                val arbeidsgiverNotifikasjonProdusent = ArbeidsgiverNotifikasjonProdusent(env.urlEnv, azureAdTokenConsumer)
                val arbeidsgiverNotifikasjonService = ArbeidsgiverNotifikasjonService(arbeidsgiverNotifikasjonProdusent)

                val beskjedKafkaProducer = BeskjedKafkaProducer(env)
                val dineSykmeldteHendelseKafkaProducer = DineSykmeldteHendelseKafkaProducer(env)

                val accessControl = AccessControl(pdlConsumer, dkifConsumer)
                val sykmeldingService = SykmeldingService(sykmeldingerConsumer)
                val syketilfelleService = SyketilfelleService(database)
                val varselSendtService = VarselSendtService(pdlConsumer, oppfolgingstilfelleConsumer, database)
                val merVeiledningVarselPlanner = MerVeiledningVarselPlanner(database, oppfolgingstilfelleConsumer, varselSendtService)
                val merVeiledningVarselPlannerSyketilfelle = MerVeiledningVarselPlannerSyketilfelle(database, syketilfelleService, varselSendtService)
                val aktivitetskravVarselPlanner = AktivitetskravVarselPlanner(database, oppfolgingstilfelleConsumer, sykmeldingService)
                val aktivitetskravVarselPlannerSyketilfelle = AktivitetskravVarselPlannerSyketilfelle(database, syketilfelleService, sykmeldingService)
                val svarMotebehovVarselPlanner = SvarMotebehovVarselPlanner(database, oppfolgingstilfelleConsumer, varselSendtService)
                val svarMotebehovVarselPlannerSyketilfelle = SvarMotebehovVarselPlannerSyketilfelle(database, syketilfelleService, varselSendtService)
                val replanleggingService = ReplanleggingService(database, merVeiledningVarselPlanner, aktivitetskravVarselPlanner)
                val narmesteLederService = NarmesteLederService(narmesteLederConsumer)
                val brukernotifikasjonerService = BrukernotifikasjonerService(beskjedKafkaProducer, accessControl)
                val motebehovVarselService = MotebehovVarselService(
                    dineSykmeldteHendelseKafkaProducer,
                    brukernotifikasjonerService,
                    arbeidsgiverNotifikasjonService,
                    narmesteLederService,
                    env.urlEnv.dialogmoterUrl
                )

                val syfoMotebehovConsumer = SyfoMotebehovConsumer(env.urlEnv, stsConsumer)

                connector {
                    port = env.appEnv.applicationPort
                }

                module {
                    state.running = true

                    serverModule(
                        env,
                        accessControl,
                        varselSendtService,
                        replanleggingService,
                        beskjedKafkaProducer,
                        dineSykmeldteHendelseKafkaProducer,
                        narmesteLederService,
                        syfoMotebehovConsumer,
                        arbeidsgiverNotifikasjonService,
                    )

                    kafkaModule(
                        env,
                        accessControl,
                        aktivitetskravVarselPlanner,
                        aktivitetskravVarselPlannerSyketilfelle,
                        merVeiledningVarselPlanner,
                        merVeiledningVarselPlannerSyketilfelle,
                        svarMotebehovVarselPlanner,
                        svarMotebehovVarselPlannerSyketilfelle,
                        motebehovVarselService
                    )

                    varselBusModule(
                        env,
                        dineSykmeldteHendelseKafkaProducer,
                        motebehovVarselService
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

private fun getStsConsumer(urlEnv: UrlEnv, authEnv: AuthEnv): TokenConsumer {
    if (isLocal()) {
        return LocalStsConsumer(urlEnv, authEnv)
    }
    return StsConsumer(urlEnv, authEnv)
}

private fun getPdlConsumer(urlEnv: UrlEnv, azureADConsumer: TokenConsumer, stsConsumer: TokenConsumer): PdlConsumer {
    return when {
        isLocal() -> LocalPdlConsumer(urlEnv, azureADConsumer)
        isGCP() -> PdlConsumer(urlEnv, azureADConsumer)
        else -> PdlConsumer(urlEnv, stsConsumer)
    }
}

private fun getDkifConsumer(urlEnv: UrlEnv, azureADConsumer: TokenConsumer, stsConsumer: TokenConsumer): DkifConsumer {
    return when {
        isLocal() -> DkifConsumer(urlEnv, azureADConsumer)
        isGCP() -> DkifConsumer(urlEnv, azureADConsumer)
        else -> DkifConsumer(urlEnv, stsConsumer)
    }
}

private fun getSyfosyketilfelleConsumer(urlEnv: UrlEnv, tokenConsumer: TokenConsumer): SyfosyketilfelleConsumer {
    if (isLocal()) {
        return LocalSyfosyketilfelleConsumer(urlEnv, tokenConsumer)
    }
    return SyfosyketilfelleConsumer(urlEnv, tokenConsumer)
}

fun Application.serverModule(
    env: Environment,
    accessControl: AccessControl,
    varselSendtService: VarselSendtService,
    replanleggingService: ReplanleggingService,
    beskjedKafkaProducer: BeskjedKafkaProducer,
    dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    narmesteLederService: NarmesteLederService,
    syfoMotebehovConsumer: SyfoMotebehovConsumer,
    arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
) {

    val sendVarselService =
        SendVarselService(
            beskjedKafkaProducer,
            dineSykmeldteHendelseKafkaProducer,
            narmesteLederService,
            accessControl,
            env.urlEnv,
            syfoMotebehovConsumer,
            arbeidsgiverNotifikasjonService,
        )

    val varselSender = VarselSender(
        database,
        sendVarselService,
        env.toggleEnv,
        env.appEnv
    )

    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    runningInGCPCluster {
        log.info("GRANTING ACCESS TO IAM ...")
        database.grantAccessToIAMUsers()
        log.info("ACCESS GRANTED")
    }

    runningRemotely {
        setupRoutesWithAuthentication(varselSender, varselSendtService, replanleggingService, env.authEnv)
    }

    runningLocally {
        setupLocalRoutesWithAuthentication(varselSender, varselSendtService, replanleggingService, env.authEnv)
    }

    routing {
        registerPrometheusApi()
        registerNaisApi(state)
    }

    state.initialized = true
}

fun Application.kafkaModule(
    env: Environment,
    accessControl: AccessControl,
    aktivitetskravVarselPlanner: AktivitetskravVarselPlanner,
    aktivitetskravVarselPlannerSyketilfelle: AktivitetskravVarselPlannerSyketilfelle,
    merVeiledningVarselPlanner: MerVeiledningVarselPlanner,
    merVeiledningVarselPlannerSyketilfelle: MerVeiledningVarselPlannerSyketilfelle,
    svarMotebehovVarselPlanner: SvarMotebehovVarselPlanner,
    svarMotebehovVarselPlannerSyketilfelle: SvarMotebehovVarselPlannerSyketilfelle,
    motebehovVarselService: MotebehovVarselService
) {
    runningRemotely {

        runningInFSSCluster {
            launch(backgroundTasksContext) {
                launchKafkaListener(
                    state,
                    OppfolgingstilfelleKafkaConsumer(env, accessControl)
                        .addPlanner(aktivitetskravVarselPlanner)
                        .addPlanner(merVeiledningVarselPlanner)
                        .addPlanner(svarMotebehovVarselPlanner)
                )
            }
        }

        runningInGCPCluster {
            launch(backgroundTasksContext) {
                launchKafkaListener(
                    state,
                    SyketilfelleKafkaConsumer(env, accessControl, database)
                        .addPlanner(merVeiledningVarselPlannerSyketilfelle)
                        .addPlanner(aktivitetskravVarselPlannerSyketilfelle)
                        .addPlanner(svarMotebehovVarselPlannerSyketilfelle)
                )
            }

            if (env.toggleEnv.useKandidatlisteTopic) {
                launch(backgroundTasksContext) {
                    launchKafkaListener(
                        state,
                        KandidatlisteDialogmoteKafkaConsumer(env, accessControl, motebehovVarselService)
                    )
                }
            }
        }
    }
}

fun Application.varselBusModule(
    env: Environment,
    dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    motebehovVarselService: MotebehovVarselService
) {
    runningRemotely {
        runningInGCPCluster {
            val varselBusService =
                VarselBusService(dineSykmeldteHendelseKafkaProducer, motebehovVarselService)

            launch(backgroundTasksContext) {
                launchKafkaListener(
                    state,
                    VarselBusKafkaConsumer(env, varselBusService)
                )
            }
        }
    }
}

val Application.envKind
    get() = environment.config.property("ktor.environment").getString()

val Application.cluster
    get() = environment.config.property("ktor.cluster").getString()

fun Application.runningInFSSCluster(block: () -> Unit) {
    if (cluster.contains("fss")) block()
}

fun Application.runningInGCPCluster(block: () -> Unit) {
    if (cluster.contains("gcp")) block()
}

fun Application.runningRemotely(block: () -> Unit) {
    if (envKind == "remote") block()
}

fun Application.runningLocally(block: () -> Unit) {
    if (envKind == "local") block()
}

fun initDb(dbEnv: DbEnv): DatabaseInterface =
    when {
        isLocal() -> localDatabase(dbEnv)
        isGCP() -> Database(dbEnv)
        else -> remoteDatabase(dbEnv)
    }

private fun localDatabase(dbEnv: DbEnv): DatabaseInterface = LocalDatabase(dbEnv)

private fun remoteDatabase(dbEnv: DbEnv): DatabaseInterface = RemoteDatabase(dbEnv)
