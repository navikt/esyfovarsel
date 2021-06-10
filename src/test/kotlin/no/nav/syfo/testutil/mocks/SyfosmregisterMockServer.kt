package no.nav.syfo.testutil.mocks

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.Environment
import no.nav.syfo.testutil.extractPortFromUrl

class SyfosmregisterMockServer(env: Environment) {
    val url = env.syfosmregisterUrl
    private val port = url.extractPortFromUrl()

    fun mockServer(): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            routing {
                post("/api/v1/docs/index.html#/default/sykmeldtStatus") {
                    call.respond(
                        sykmeldtStatusResponse
                    )
                }
            }
        }
    }
}
