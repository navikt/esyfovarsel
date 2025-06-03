package no.nav.syfo.testutil.mocks

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.syfo.AuthEnv
import no.nav.syfo.UrlEnv
import no.nav.syfo.consumer.dkif.PostPersonerRequest
import no.nav.syfo.testutil.extractPortFromUrl

class MockServers(val urlEnv: UrlEnv, val authEnv: AuthEnv) {

    fun mockDkifServer(): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return mockServer(urlEnv.dkifUrl) {
            val jsonMapper = jacksonObjectMapper()
            post("/rest/v1/personer") {
                val requestBody = jsonMapper.readValue(call.receiveText(), PostPersonerRequest::class.java)
                if (requestBody.personidenter.contains("serverdown")) {
                    call.response.status(HttpStatusCode(500, "Server error"))
                } else {
                    call.respondText(
                        jsonMapper.writeValueAsString(dkifPostPersonerResponse),
                        ContentType.Application.Json,
                        HttpStatusCode.OK
                    )
                }
            }
        }
    }

    fun mockAADServer(): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return mockServer(authEnv.aadAccessTokenUrl) {
            post {
                call.respond(tokenFromAzureServer)
            }
        }
    }

    @Suppress("MaxLineLength")
    fun mockArbeidsgiverNotifikasjonServer(): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return mockServer(urlEnv.arbeidsgiverNotifikasjonProdusentApiUrl) {
            val responseNyBeskjed = """
                {
                  "data": {
                    "nyBeskjed": {
                      "__typename": "NyBeskjedVellykket",
                      "id": "d69f8c4f-8d34-47b0-9539-d3c2e54115da"
                    }
                  }
                }
            """
            val responseNyOppgave = """
                {
                  "data": {
                    "nyOppgave": {
                      "__typename": "NyOppgaveVellykket",
                      "id": "d69f8c4f-8d34-47b0-9539-d3c2e54115da"
                    }
                  }
                }
            """
            post("/") {
                val body = call.receiveText()
                if (body.contains("OpprettNyOppgave")) {
                    call.respondText(responseNyOppgave, ContentType.Application.Json)
                } else if (body.contains("OpprettNyBeskjed")) {
                    call.respondText(responseNyBeskjed, ContentType.Application.Json)
                } else {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    @Suppress("MaxLineLength")
    fun mockJournalpostdistribusjonServer(): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return mockServer(urlEnv.dokdistfordelingUrl) {
            val bestillingsId = """
                {
                  "bestillingsId": "1000"
                }
            """
            post("/rest/v1/distribuerjournalpost") {
                val body = call.receiveText()
                if (body.contains("GONE")) {
                    call.response.status(HttpStatusCode(410, "Recipient is gone"))
                } else if (body.contains("CONFLICT")) {
                    call.response.status(HttpStatusCode(410, "Recipient is gone"))
                    call.respondText(bestillingsId, ContentType.Application.Json, HttpStatusCode.Conflict)
                } else {
                    call.respondText(bestillingsId, ContentType.Application.Json, HttpStatusCode.OK)
                }
            }
        }
    }

    fun mockServer(
        url: String,
        route: Route.() -> Unit
    ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
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
