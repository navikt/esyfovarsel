package no.nav.syfo.consumer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.consumer.pdl.*
import org.slf4j.LoggerFactory

@KtorExperimentalAPI
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

    fun getFnr(aktorId: String): String? {
        val response = callPdl(IDENTER_QUERY, aktorId)

        return when (response?.status) {
            HttpStatusCode.OK -> {
                runBlocking { response.receive<PdlIdentResponse>().data?.hentIdenter?.identer?.first()?.ident }
            }
            HttpStatusCode.NoContent -> {
                log.error("Could not get fnr from PDL: No content found in the response body")
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not get fnr from PDL: Unable to authorize")
                null
            }
            else -> {
                log.error("Could not get fnr from PDL: $response")
                null
            }
        }
    }

    fun isBrukerGradert(aktorId: String): Boolean? {
        val response = callPdl(PERSON_QUERY, aktorId)

        return when (response?.status) {
            HttpStatusCode.OK -> {
                runBlocking { response.receive<PdlPersonResponse>().data?.isKode6Eller7() }
            }
            HttpStatusCode.NoContent -> {
                log.error("Could not get adressesperre from PDL: No content found in the response body")
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not get adressesperre from PDL: Unable to authorize")
                null
            }
            else -> {
                log.error("Could not get adressesperre from PDL: $response")
                null
            }
        }
    }

    fun callPdl(service: String, aktorId: String): HttpResponse? {
        return runBlocking {
            val stsToken = stsConsumer.getToken()
            val bearerTokenString = "Bearer ${stsToken.access_token}"
            val graphQuery = this::class.java.getResource("$QUERY_PATH_PREFIX/$service").readText().replace("[\n\r]", "")
            val requestBody = PdlRequest(graphQuery, Variables(aktorId))

            try {
                client.post<HttpResponse>(pdlBasepath) {
                    headers {
                        append(TEMA_HEADER, OPPFOLGING_TEMA_HEADERVERDI)
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        append(HttpHeaders.Authorization, bearerTokenString)
                        append(NAV_CONSUMER_TOKEN_HEADER, bearerTokenString)
                    }
                    body = requestBody
                }
            } catch (e: Exception) {
                log.error("Error while calling PDL ($service): ${e.message}")
                null
            }
        }
    }
}
