package no.nav.syfo.consumer.distribuerjournalpost

import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.exceptions.JournalpostDistribusjonException
import no.nav.syfo.exceptions.JournalpostDistribusjonGoneException
import no.nav.syfo.exceptions.JournalpostNetworkException
import no.nav.syfo.utils.httpClientWithRetry
import org.slf4j.LoggerFactory
import java.io.IOException

class JournalpostdistribusjonConsumer(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClientWithRetry(expectSuccess = false)
    private val dokdistfordelingUrl = urlEnv.dokdistfordelingUrl
    private val dokdistfordelingScope = urlEnv.dokdistfordelingScope

    private val log = LoggerFactory.getLogger(JournalpostdistribusjonConsumer::class.java)

    suspend fun distribuerJournalpost(
        journalpostId: String,
        uuid: String,
        distribusjonstype: DistibusjonsType,
        tvingSentralPrint: Boolean = false,
    ): JournalpostdistribusjonResponse {
        val requestURL = "$dokdistfordelingUrl/rest/v1/distribuerjournalpost"
        val token = azureAdTokenConsumer.getToken(dokdistfordelingScope)
        val request = JournalpostdistribusjonRequest(
            journalpostId = journalpostId,
            distribusjonstype = distribusjonstype.name,
            tvingKanal = if (tvingSentralPrint) Kanal.PRINT else null,
        )

        return try {
            log.debug("Attempting to distribute journalpost with ID: $journalpostId and UUID: $uuid")
            val response = client.post(requestURL) {
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json)
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(request)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    log.info("Successfully sent document with ID: $journalpostId and UUID: $uuid to print")
                    response.body()
                }
                HttpStatusCode.Conflict -> {
                    log.info("Document with UUID: $uuid and journalpostId: $journalpostId already sent to print")
                    response.body()
                }
                HttpStatusCode.Gone -> {
                    log.info(
                        "Document with UUID: $uuid and journalpostId: $journalpostId  will never be sent. " +
                            "The receiver is flagged as Gone."
                    )
                    throw JournalpostDistribusjonGoneException(
                        "Failed to distribution journalpostId $journalpostId. Resource is Gone. " +
                            "Status: ${response.status}"
                    )
                }
                else -> {
                    log.error(
                        "Failed to send document with UUID: $uuid to print. " +
                            "JournalpostId: $journalpostId. Response status: ${response.status}"
                    )
                    throw JournalpostDistribusjonException(
                        "Failed to send document to print. Status: ${response.status}"
                    )
                }
            }
        } catch (e: JournalpostDistribusjonGoneException) {
            throw e
        } catch (e: IOException) {
            log.error("Network error while distributing journalpost $journalpostId: ${e.message}", e)
            throw JournalpostNetworkException("Network error distributing journalpost", uuid, journalpostId, e)
        } catch (e: Exception) {
            log.error("Exception while distributing journalpost $journalpostId: ${e.javaClass.name}: ${e.message}", e)
            throw JournalpostDistribusjonException("Failed to distribute journalpost", uuid, journalpostId, e)
        }
    }
}
