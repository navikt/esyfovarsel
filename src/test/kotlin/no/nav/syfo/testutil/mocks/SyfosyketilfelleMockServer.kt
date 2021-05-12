package no.nav.syfo.testutil.mocks

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import no.nav.syfo.Environment
import no.nav.syfo.testutil.extractPortFromUrl

class SyfosyketilfelleMockServer(env: Environment)  {
    val url = env.syfosyketilfelleUrl
    private val port = url.extractPortFromUrl()

    fun mockServer() : NettyApplicationEngine {
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
                get("/kafka/oppfolgingstilfelle/beregn/{aktorId}/{orgnr}") {
                    call.respond(oppfolgingstilfelleResponse
                        .copy(
                            aktorId = call.parameters["aktorId"] ?: aktorId,
                            orgnummer = call.parameters["orgnr"] ?: orgnummer
                        )
                    )
                }
            }
        }
    }
}
