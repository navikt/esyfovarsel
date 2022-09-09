package no.nav.syfo.consumer.distribuerjournalpost

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory

class JournalpostdistribusjonConsumer(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()
    private val dokdistfordelingUrl = urlEnv.dokdistfordelingUrl
    private val dokdistfordelingScope = urlEnv.dokdistfordelingScope

    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.JournalpostdistribusjonConsumer")

    suspend fun distribuerJournalpost(journalpostId: String): JournalpostdistribusjonResponse? {
        val requestURL = "${dokdistfordelingUrl}/rest/v1/distribuerjournalpost"
        val token = azureAdTokenConsumer.getToken(dokdistfordelingScope)
        val request = JournalpostdistribusjonRequest(
            journalpostId = journalpostId,
        )

        return runBlocking {
            try {
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
                        response.body<JournalpostdistribusjonResponse>()
                    }
                    HttpStatusCode.Unauthorized -> {
                        log.error("Failed to send document to print: Unable to authorize, journalpostId: $journalpostId")
                        null
                    }
                    else -> {
                        log.error("Error while calling distribuerjournalpost, journalpostId: $journalpostId; response: $response")
                        null
                    }
                }

            } catch (e: Exception) {
                log.error("Exception while calling distribuerjournalpost, journalpostId: $journalpostId; error message: ${e.message}", e)
                null
            }
        }
    }
}
