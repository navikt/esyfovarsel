package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.application.Application
import io.ktor.config.HoconApplicationConfig
import io.ktor.routing.routing
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.db.*
import no.nav.syfo.kafka.oppfolgingstilfelle.OppfolgingstilfelleKafkaConsumer
import no.nav.syfo.kafka.launchKafkaListener
import java.util.concurrent.Executors


data class ApplicationState(var running: Boolean = false, var initialized: Boolean = false)

val env: Environment = getEnvironment()
val state: ApplicationState = ApplicationState()
val backgroundTasksContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
lateinit var database: DatabaseInterface

fun main() {
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

fun Application.init() {

    runningLocally {
        database = LocalDatabase(DbConfig(
            jdbcUrl = env.databaseUrl,
            databaseName = env.databaseName,
            password = "password",
            username = "esyfovarsel-admin",
            remote = false)
        )

        state.running = true
    }

    runningRemotely {
        database = RemoteDatabase(DbConfig(
            jdbcUrl = env.databaseUrl,
            databaseName = env.databaseName,
            dbCredMountPath = env.dbVaultMountPath)
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

fun Application.kafkaModule() {

    runningRemotely {
        val stsConsumer = StsConsumer(env)
        val oppfolgingstilfelleConsumer = SyfosyketilfelleConsumer(env, stsConsumer)
        val oppfolgingstilfelleKafkaConsumer = OppfolgingstilfelleKafkaConsumer(env, oppfolgingstilfelleConsumer)

        launch(backgroundTasksContext) {
            launchKafkaListener(
                state,
                oppfolgingstilfelleKafkaConsumer
            )
        }
    }
}

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.runningLocally(block: () -> Unit) {
    if (envKind == "local") block()
}

fun Application.runningRemotely(block: () -> Unit) {
    if (envKind == "remote") block()
}
