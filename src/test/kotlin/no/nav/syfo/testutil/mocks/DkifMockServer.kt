package no.nav.syfo.testutil.mocks

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import no.nav.syfo.Environment
import no.nav.syfo.consumer.DkifConsumer.Companion.NAV_PERSONIDENTER_HEADER
import no.nav.syfo.testutil.extractPortFromUrl

class DkifMockServer(env: Environment)  {
    val url = env.dkifUrl
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
                get("/api/v1/personer/kontaktinformasjon") {
                    if (call.request.headers[NAV_PERSONIDENTER_HEADER]?.isValidHeader() == true)
                        call.respond(dkifResponseMap[call.request.headers[NAV_PERSONIDENTER_HEADER]] ?: dkifResponseSuccessKanVarsles)
                    else
                        call.response.status(HttpStatusCode(500, "Server error"))
                }
            }
        }
    }

    fun String.isValidHeader() : Boolean {
        return this.all { char -> char.isDigit() }
    }
}
