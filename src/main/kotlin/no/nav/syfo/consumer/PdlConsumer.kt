package no.nav.syfo.consumer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.engine.cio.CIO
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.statement.HttpResponse
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.post
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.Environment
import no.nav.syfo.consumer.pdl.*
import org.slf4j.LoggerFactory
import java.io.File

class PdlConsumer(env: Environment, stsConsumer: StsConsumer) {
    private val client: HttpClient
    private val stsConsumer: StsConsumer
    private val pdlBasepath: String
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.PdlConsuner")

    init {
        client = HttpClient(CIO) {
            install(JsonFeature) {
                serializer = JacksonSerializer {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
        }
        this.stsConsumer = stsConsumer
        pdlBasepath = env.pdlUrl
    }

    suspend fun getFnr(aktorId: String) : String? {
        val stsToken = stsConsumer.getToken()
        val bearerTokenString = "Bearer ${stsToken.access_token}"
        val graphQuery = this::class.java.getResource("/pdl/hentIdenter.graphql").readText().replace("[\n\r]", "")
        val requestBody = PdlRequest(graphQuery, Variables(aktorId))

        val response = try {
            client.post<HttpResponse>(pdlBasepath) {
                headers {
                    append(TEMA_HEADER, OPPFOLGING_TEMA_HEADERVERDI)
                    append(HttpHeaders.ContentType, APPLICATION_JSON)
                    append(HttpHeaders.Authorization, bearerTokenString)
                    append(NAV_CONSUMER_TOKEN_HEADER, bearerTokenString)
                }
                body = requestBody
            }
        }   catch (e: Exception) {
            log.error("Error while calling PDL: ${e.message}")
            null
        }

        return when (response?.status) {
            HttpStatusCode.OK -> {
                response.receive<PdlIdentResponse>().data?.hentIdenter?.identer?.first()?.ident
            }
            HttpStatusCode.NoContent -> {
                log.error("Could not get oppfolgingstilfelle: No content found in the response body")
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not get oppfolgingstilfelle: Unable to authorize")
                null
            }
            else -> {
                log.error("Could not get oppfolgingstilfelle: $response")
                null
            }
        }

    }
}
