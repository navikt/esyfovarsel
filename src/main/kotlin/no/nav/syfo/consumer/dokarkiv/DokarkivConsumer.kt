package no.nav.syfo.consumer.dokarkiv

import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.dokarkiv.domain.DokarkivRequest
import no.nav.syfo.consumer.dokarkiv.domain.DokarkivResponse
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory

class DokarkivConsumer(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()
    private val dokarkivUrl = urlEnv.dokarkivUrl
    private val dokarkivScope = urlEnv.dokarkivScope

    private val JOURNALPOST_PATH = "/rest/journalpostapi/v1/journalpost"
    private val JOURNALPOST_PARAM_STRING = "forsoekFerdigstill"
    private val JOURNALPOST_PARAM_VALUE = true
    private val requestURL: String = "$dokarkivUrl$JOURNALPOST_PATH"

    private val log = LoggerFactory.getLogger(DokarkivConsumer::class.qualifiedName)

    suspend fun postDocumentToDokarkiv(request: DokarkivRequest): DokarkivResponse? {
        try {
            val token = azureAdTokenConsumer.getToken(dokarkivScope)
            val response = client.post(requestURL) {
                parameter(JOURNALPOST_PARAM_STRING, JOURNALPOST_PARAM_VALUE)
                header(HttpHeaders.Authorization, "Bearer $token")
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            return when (response.status) {
                HttpStatusCode.Created -> {
                    log.info("Sending to dokarkiv successful, journalpost created")
                    response.body<DokarkivResponse>()
                }

                HttpStatusCode.Unauthorized -> {
                    log.error("Failed to post document to Dokarkiv: Unable to authorize")
                    null
                }

                else -> {
                    log.error("Failed to post document to Dokarkiv: $response")
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Exception while posting document to Dokarkiv, message: ${e.message}")
            return null
        } catch (e: Error) {
            log.error("Error while post documenting to Dokarkiv, message: ${e.message}")
            throw e
        }
    }
}
