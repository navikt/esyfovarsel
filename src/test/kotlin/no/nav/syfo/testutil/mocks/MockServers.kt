package no.nav.syfo.testutil.mocks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.AuthEnv
import no.nav.syfo.UrlEnv
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.consumer.pdl.IDENTER_QUERY
import no.nav.syfo.consumer.pdl.PERSON_QUERY
import no.nav.syfo.testutil.extractPortFromUrl
import java.io.Serializable

class MockServers(val urlEnv: UrlEnv, val authEnv: AuthEnv) {
    private val mapper = ObjectMapper().registerModule(KotlinModule())

    fun mockPdlServer(): NettyApplicationEngine {
        return mockServer(urlEnv.pdlUrl) {
            post("/") {
                val content = call.receiveText()
                val queryType = content.queryType()
                val pdlRequest: PdlRequestSerializable = mapper.readValue(content)
                val aktorId = pdlRequest.variables.ident
                val response: Any? = when (queryType) {
                    PERSON_QUERY -> pdlGetBrukerReservert[aktorId]
                    IDENTER_QUERY -> pdlGetFnrResponseMap[aktorId]
                    else -> null
                }
                call.respond(response ?: "")
            }
        }
    }

    fun mockDkifServer(): NettyApplicationEngine {
        return mockServer(urlEnv.dkifUrl) {
            get("/api/v1/personer/kontaktinfo") {
                if (call.request.headers[DkifConsumer.NAV_PERSONIDENTER_HEADER]?.isValidHeader() == true) {
                    call.respond(
                        dkifResponseMap[call.request.headers[DkifConsumer.NAV_PERSONIDENTER_HEADER]]
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

    fun mockSyfosyketilfelleServer(): NettyApplicationEngine {
        return mockServer(urlEnv.syfosyketilfelleUrl) {
            get("/kafka/oppfolgingstilfelle/beregn/{aktorId}") {
                call.respond(
                    oppfolgingstilfelleResponse
                        .copy(
                            aktorId = call.parameters["aktorId"] ?: aktorId
                        )
                )
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

fun String.queryType(): String? {
    if (this.contains("hentPerson"))
        return PERSON_QUERY
    if (this.contains("hentIdenter"))
        return IDENTER_QUERY
    return null
}

data class PdlRequestSerializable(
    val query: String,
    val variables: VariablesSerializable
) : Serializable

data class VariablesSerializable(
    val ident: String
) : Serializable
