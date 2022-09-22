package no.nav.syfo.testutil.mocks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.AuthEnv
import no.nav.syfo.UrlEnv
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.testutil.extractPortFromUrl

class MockServers(val urlEnv: UrlEnv, val authEnv: AuthEnv) {
    private val mapper = ObjectMapper().registerModule(KotlinModule())

    fun mockDkifServer(): NettyApplicationEngine {
        return mockServer(urlEnv.dkifUrl) {
            get("/rest/v1/person") {
                if (call.request.headers[DkifConsumer.NAV_PERSONIDENT_HEADER]?.isValidHeader() == true) {
                    call.respond(
                        dkifResponseMap[call.request.headers[DkifConsumer.NAV_PERSONIDENT_HEADER]]
                            ?: dkifResponseSuccessKanVarslesResponseJSON
                    )
                } else {
                    call.response.status(HttpStatusCode(500, "Server error"))
                }
            }
        }
    }

    fun mockAADServer(): NettyApplicationEngine {
        return mockServer(authEnv.aadAccessTokenUrl) {
            post {
                call.respond(tokenFromAzureServer)
            }
        }
    }
    fun mockServer(url: String, route: Route.() -> Unit): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = url.extractPortFromUrl()
        ) {
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            routing {
                route(this)
            }
        }
    }
}

fun String.isValidHeader(): Boolean {
    return this.all { char -> char.isDigit() }
}
