package no.nav.syfo.consumer.dokarkiv

import io.ktor.client.call.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.dokarkiv.domain.DokarkivRequest
import no.nav.syfo.consumer.dokarkiv.domain.DokarkivResponse
import no.nav.syfo.utils.postWithParameter
import org.slf4j.LoggerFactory

class DokarkivConsumer(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val dokarkivUrl = urlEnv.dokarkivUrl
    private val dokarkivScope = urlEnv.dokarkivScope

    private val JOURNALPOST_PATH = "/rest/journalpostapi/v1/journalpost"
    private val JOURNALPOST_PARAM_STRING = "forsoekFerdigstill"
    private val JOURNALPOST_PARAM_VALUE = true
    private val requestURL: String = "$dokarkivUrl$JOURNALPOST_PATH"

    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.DokarkivClient")

    suspend fun postDocumentToDokarkiv(request: DokarkivRequest): DokarkivResponse? {
        try {
            val token = azureAdTokenConsumer.getToken(dokarkivScope)
            val response = postWithParameter(
                requestURL, request, token,
                hashMapOf(
                    HttpHeaders.Accept to ContentType.Application.Json.toString(),
                    HttpHeaders.ContentType to ContentType.Application.Json.toString(),
                ),
                Pair(JOURNALPOST_PARAM_STRING, JOURNALPOST_PARAM_VALUE)
            )

            return when (response.status) {
                HttpStatusCode.Created -> {
                    log.info("Sending to dokarkiv successful, journalpost created")
                    response.receive<DokarkivResponse>()
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
