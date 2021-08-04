package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.consumer.DkifConsumer
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.consumer.SykmeldingerConsumer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.DbConfig
import no.nav.syfo.db.LocalDatabase
import no.nav.syfo.db.RemoteDatabase
import no.nav.syfo.job.SendVarslerJobb
import no.nav.syfo.kafka.launchKafkaListener
import no.nav.syfo.kafka.oppfolgingstilfelle.OppfolgingstilfelleKafkaConsumer
import no.nav.syfo.service.AccessControl
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.varsel.AktivitetskravVarselPlanner
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = false, var initialized: Boolean = false)

val env: Environment = getEnvironment()
val state: ApplicationState = ApplicationState()
val backgroundTasksContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
lateinit var database: DatabaseInterface

@KtorExperimentalAPI
fun main() {

    val sendeVarsler = System.getenv("SEND_VARSLER") ?: "NEI"
    if (sendeVarsler == "JA") {
        val jobb = SendVarslerJobb()
        sendVarsler(jobb)
    } else {
        val server = embeddedServer(Netty, applicationEngineEnvironment {
            log = LoggerFactory.getLogger("ktor.application")
            config = HoconApplicationConfig(ConfigFactory.load())

            connector {
                port = env.applicationPort
            }

            module {
                init()
                serverModule()
                kafkaModule()
            }
        })
        Runtime.getRuntime().addShutdownHook(Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        })

        server.start(wait = false)
    }
}

fun sendVarsler(jobb: SendVarslerJobb) = jobb.sendVarsler()

@KtorExperimentalAPI
fun Application.init() {

    runningLocally {
        database = LocalDatabase(
            DbConfig(
                jdbcUrl = env.databaseUrl,
                databaseName = env.databaseName,
                password = "password",
                username = "esyfovarsel-admin",
                remote = false
            )
        )

        state.running = true
    }

    runningRemotely {
        database = RemoteDatabase(
            DbConfig(
                jdbcUrl = env.databaseUrl,
                databaseName = env.databaseName,
                dbCredMountPath = env.dbVaultMountPath
            )
        )
        state.running = true
    }
}

fun Application.serverModule() {

    routing {
        registerNaisApi(state)
    }

    state.initialized = true
}

@KtorExperimentalAPI
fun Application.kafkaModule() {

    runningRemotely {
        val stsConsumer = StsConsumer(env)
        val azureAdTokenConsumer = AzureAdTokenConsumer(env)
        val pdlConsumer = PdlConsumer(env, stsConsumer)
        val dkifConsumer = DkifConsumer(env, stsConsumer)
        val oppfolgingstilfelleConsumer = SyfosyketilfelleConsumer(env, stsConsumer)
        val accessControl = AccessControl(pdlConsumer, dkifConsumer)
        val sykmeldingerConsumer = SykmeldingerConsumer(env, azureAdTokenConsumer)
        val sykmeldingService = SykmeldingService(sykmeldingerConsumer)

        val oppfolgingstilfelleKafkaConsumer = OppfolgingstilfelleKafkaConsumer(env, accessControl)
            .addPlanner(AktivitetskravVarselPlanner(database, oppfolgingstilfelleConsumer, sykmeldingService))

        launch(backgroundTasksContext) {
            launchKafkaListener(
                state,
                oppfolgingstilfelleKafkaConsumer
            )
        }
    }
}

@KtorExperimentalAPI
val Application.envKind
    get() = environment.config.property("ktor.environment").getString()

@KtorExperimentalAPI
fun Application.runningLocally(block: () -> Unit) {
    if (envKind == "local") block()
}

@KtorExperimentalAPI
fun Application.runningRemotely(block: () -> Unit) {
    if (envKind == "remote") block()
}
