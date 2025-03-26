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
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory

class JournalpostdistribusjonConsumer(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()
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
                    log.info("Sent document to print")
                    response.body()
                }
                HttpStatusCode.Conflict -> {
                    log.info(
                        """Document with uuid $uuid and journalpostId $journalpostId already sent to print.
                        Response: ${response.status}
                        """.trimIndent()
                    )
                    response.body()
                }
                else -> {
                    throw RuntimeException(
                        """Failed to send document with uuid $uuid to print. journalpostId: $journalpostId.
                            Response status: ${response.status}. Response: $response
                        """.trimMargin()
                    )
                }
            }
        } catch (e: Exception) {
            throw RuntimeException(
                "Exception while calling distribuerjournalpost with uuid $uuid and journalpostId: $journalpostId. Error message: ${e.message}",
                e,
            )
        }
    }
}
