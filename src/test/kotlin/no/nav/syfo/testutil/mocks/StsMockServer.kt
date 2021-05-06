package no.nav.syfo.testutil.mocks

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import no.nav.syfo.Environment
import no.nav.syfo.testutil.extractPortFromUrl

class StsMockServer(env: Environment)  {
    val url = env.stsUrl
    private val port = url.extractPortFromUrl()

    fun mockServer() : NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            install(ContentNegotiation) {
                jackson {}
            }
            routing {
                post("/rest/v1/sts/token") {
                    call.respond(tokenFromStsServer)
                }
            }
        }
    }
}

