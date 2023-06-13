package no.nav.syfo.testutil.mocks

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.syfo.AuthEnv
import no.nav.syfo.UrlEnv
import no.nav.syfo.testutil.extractPortFromUrl
import no.nav.syfo.utils.NAV_PERSONIDENT_HEADER

class MockServers(val urlEnv: UrlEnv, val authEnv: AuthEnv) {

    fun mockDkifServer(): NettyApplicationEngine {
        return mockServer(urlEnv.dkifUrl) {
            get("/rest/v1/person") {
                if (call.request.headers[NAV_PERSONIDENT_HEADER]?.isValidHeader() == true) {
                    call.respond(
                        dkifResponseMap[call.request.headers[NAV_PERSONIDENT_HEADER]]
                            ?: dkifResponseSuccessKanVarslesResponseJSON,
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
            port = url.extractPortFromUrl(),
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
