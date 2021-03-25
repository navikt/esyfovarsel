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
import no.nav.syfo.db.*


data class ApplicationState(var running: Boolean = false, var initialized: Boolean = false)

val env: Environment = getEnvironment()
val state: ApplicationState = ApplicationState()
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
            databaseName = env.databaseName)
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


fun CoroutineScope.createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job {
    return launch {
        try {
            action()
        } finally {
            applicationState.running = false
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
