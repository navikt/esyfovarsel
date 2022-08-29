package no.nav.syfo.consumer.dokarkiv

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.dokarkiv.domain.DokarkivRequest
import no.nav.syfo.consumer.dokarkiv.domain.DokarkivResponse
import org.slf4j.LoggerFactory

class DokarkivConsumer(urlEnv: UrlEnv, azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client: HttpClient
    private val azureAdTokenConsumer: AzureAdTokenConsumer
    private val basepath: String
    private val dokarkivUrl = urlEnv.dokarkivUrl
    private val dokarkivScope = urlEnv.dokarkivScope

    private val JOURNALPOST_PATH = "/rest/journalpostapi/v1/journalpost"
    private val JOURNALPOST_PARAM_STRING = "forsoekFerdigstill"
    private val JOURNALPOST_PARAM_VALUE = true
    private val requestURL: String = "$dokarkivUrl$JOURNALPOST_PATH"

    private val LOG = LoggerFactory.getLogger("no.nav.syfo.consumer.DokarkivClient")

    init {
        client = HttpClient(CIO) {
            expectSuccess = false
            install(JsonFeature) {
                serializer = JacksonSerializer {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
        }
        this.azureAdTokenConsumer = azureAdTokenConsumer
        basepath = urlEnv.narmestelederUrl
    }

    suspend fun postDocumentToDokarkiv(request: DokarkivRequest): DokarkivResponse? {
        try {
            val token = azureAdTokenConsumer.getToken(dokarkivScope)
            val response = client.post<HttpResponse>(requestURL) {
                parameter(JOURNALPOST_PARAM_STRING, JOURNALPOST_PARAM_VALUE)
                header(HttpHeaders.Authorization, "Bearer $token")
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                body = request
            }
            return when (response.status) {
                HttpStatusCode.Created -> {
                    LOG.info("Sending to dokarkiv successful, journalpost created")
                    response.receive<DokarkivResponse>()
                }
                HttpStatusCode.Unauthorized -> {
                    LOG.error("Failed to post document to Dokarkiv: Unable to authorize")
                    null
                }
                else -> {
                    LOG.error("Failed to post document to Dokarkiv: $response")
                    null
                }
            }
        } catch (e: Exception) {
            LOG.error("Exception while posting document to Dokarkiv, message: ${e.message}")
            return null
        } catch (e: Error) {
            LOG.error("Error while post documenting to Dokarkiv, message: ${e.message}")
            throw e
        }
    }
}
