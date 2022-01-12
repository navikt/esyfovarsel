package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.jackson.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.admin.registerAdminApi
import no.nav.syfo.api.bruker.registerBrukerApi
import no.nav.syfo.api.job.registerJobTriggerApi
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.auth.LocalStsConsumer
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.auth.setupRoutesWithAuthentication
import no.nav.syfo.consumer.*
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.job.VarselSender
import no.nav.syfo.kafka.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.launchKafkaListener
import no.nav.syfo.kafka.oppfolgingstilfelle.OppfolgingstilfelleKafkaConsumer
import no.nav.syfo.metrics.registerPrometheusApi
import no.nav.syfo.service.*
import no.nav.syfo.utils.httpClient
import no.nav.syfo.varsel.AktivitetskravVarselPlanner
import no.nav.syfo.varsel.MerVeiledningVarselPlanner
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = false, var initialized: Boolean = false)

val state: ApplicationState = ApplicationState()
val backgroundTasksContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
lateinit var database: DatabaseInterface

fun main() {
    if (isJob()) {
        val env = getJobEnv()
        if (env.sendVarsler) {
            runBlocking {
                httpClient().post(env.jobTriggerUrl)
            }
        } else {
            LoggerFactory.getLogger("no.nav.syfo.BootstrapApplication").info("Jobb togglet av")
        }
    } else {
        val env = getEnv()
        val server = embeddedServer(Netty, applicationEngineEnvironment {
            log = LoggerFactory.getLogger("ktor.application")
            config = HoconApplicationConfig(ConfigFactory.load())
            database = Database(env.dbEnv)

            val stsConsumer = getStsConsumer(env.urlEnv, env.authEnv)
            val pdlConsumer = getPdlConsumer(env.urlEnv, stsConsumer)
            val dkifConsumer = DkifConsumer(env.urlEnv, stsConsumer)
            val oppfolgingstilfelleConsumer = getSyfosyketilfelleConsumer(env.urlEnv, stsConsumer)
            val azureAdTokenConsumer = AzureAdTokenConsumer(env.authEnv)
            val sykmeldingerConsumer = SykmeldingerConsumer(env.urlEnv, azureAdTokenConsumer)

            val accessControl = AccessControl(pdlConsumer, dkifConsumer)
            val sykmeldingService = SykmeldingService(sykmeldingerConsumer)
            val varselSendtService = VarselSendtService(pdlConsumer, oppfolgingstilfelleConsumer, database)
            val merVeiledningVarselPlanner =
                MerVeiledningVarselPlanner(database, oppfolgingstilfelleConsumer, varselSendtService)
            val aktivitetskravVarselPlanner =
                AktivitetskravVarselPlanner(database, oppfolgingstilfelleConsumer, sykmeldingService)
            val replanleggingService =
                ReplanleggingService(database, merVeiledningVarselPlanner, aktivitetskravVarselPlanner)

            connector {
                port = env.appEnv.applicationPort
            }

            module {
                state.running = true

                serverModule(
                    env,
                    accessControl,
                    varselSendtService,
                    replanleggingService
                )

                kafkaModule(
                    env,
                    accessControl,
                    aktivitetskravVarselPlanner,
                    merVeiledningVarselPlanner
                )
            }
        })

        Runtime.getRuntime().addShutdownHook(Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        })

        server.start(wait = false)
    }
}

private fun getStsConsumer(urlEnv: UrlEnv, authEnv: AuthEnv): StsConsumer {
    if (isLocal()) {
        return LocalStsConsumer(urlEnv, authEnv)
    }
    return StsConsumer(urlEnv, authEnv)
}

private fun getPdlConsumer(urlEnv: UrlEnv, stsConsumer: StsConsumer): PdlConsumer {
    if (isLocal()) {
        return LocalPdlConsumer(urlEnv, stsConsumer)
    }
    return PdlConsumer(urlEnv, stsConsumer)
}

private fun getSyfosyketilfelleConsumer(urlEnv: UrlEnv, stsConsumer: StsConsumer): SyfosyketilfelleConsumer {
    if (isLocal()) {
        return LocalSyfosyketilfelleConsumer(urlEnv, stsConsumer)
    }
    return SyfosyketilfelleConsumer(urlEnv, stsConsumer)
}

fun Application.serverModule(
    env: Environment,
    accessControl: AccessControl,
    varselSendtService: VarselSendtService,
    replanleggingService: ReplanleggingService
) {
    val beskjedKafkaProducer = BeskjedKafkaProducer(env)
    val sendVarselService = SendVarselService(beskjedKafkaProducer, accessControl)

    val varselSender = VarselSender(
        database,
        sendVarselService,
        env.toggleEnv
    )

    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    runningRemotely {
        setupRoutesWithAuthentication(varselSender, varselSendtService, replanleggingService, env.authEnv)
    }
    runningLocally {
        routing {
            registerBrukerApi(varselSendtService)
            registerAdminApi(replanleggingService)
            registerJobTriggerApi(varselSender)
        }
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
    merVeiledningVarselPlanner: MerVeiledningVarselPlanner
) {
    runningRemotely {
        val oppfolgingstilfelleKafkaConsumer = OppfolgingstilfelleKafkaConsumer(env, accessControl)
            .addPlanner(aktivitetskravVarselPlanner)
            .addPlanner(merVeiledningVarselPlanner)

        launch(backgroundTasksContext) {
            launchKafkaListener(
                state,
                oppfolgingstilfelleKafkaConsumer
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
