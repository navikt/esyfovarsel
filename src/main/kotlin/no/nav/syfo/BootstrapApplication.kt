package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.syfo.api.registerNaisApi
import org.slf4j.LoggerFactory


data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val state: ApplicationState = ApplicationState(running = false, initialized = false)

val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())

fun main() {
    val serviceUserSecrets = ServiceUserSecrets(
            username = Files.readString(Paths.get("/var/run/secrets/serviceuser/username")),
            password = Files.readString(Paths.get("/var/run/secrets/serviceuser/password"))
    )
    val env: Environment = Environment(serviceUserSecrets)

    val server = embeddedServer(Netty, applicationEngineEnvironment {
        log = LoggerFactory.getLogger("ktor.application")

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
    state.running = true
}

fun Application.serverModule() {

    routing {
        registerNaisApi(state)
    }

    state.initialized = true
}


fun CoroutineScope.createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        launch {
            try {
                action()
            } finally {
                applicationState.running = false
            }
        }


