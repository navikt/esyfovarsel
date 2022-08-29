package no.nav.syfo.consumer.distribuerjournalpost

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
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import org.slf4j.LoggerFactory

class JournalpostdistribusjonConsumer(urlEnv: UrlEnv, azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client: HttpClient
    private val dokdistfordelingUrl: String
    private val dokdistfordelingScope: String
    private val azureAdTokenConsumer: AzureAdTokenConsumer

    private val LOG = LoggerFactory.getLogger("no.nav.syfo.consumer.JournalpostdistribusjonConsumer")

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
        }
        this.azureAdTokenConsumer = azureAdTokenConsumer
        dokdistfordelingUrl = urlEnv.dokdistfordelingUrl
        dokdistfordelingScope = urlEnv.dokdistfordelingScope
    }

    suspend fun distribuerJournalpost(journalpostId: String): JournalpostdistribusjonResponse? {
        val requestURL = "${dokdistfordelingUrl}/rest/v1/distribuerjournalpost"
        val token = azureAdTokenConsumer.getToken(dokdistfordelingScope)
        val request = JournalpostdistribusjonRequest(
            journalpostId = journalpostId,
        )

        return runBlocking {
            try {
                val response = client.post<HttpResponse>(requestURL) {
                    headers {
                        append(HttpHeaders.Accept, ContentType.Application.Json)
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                    body = request
                }

                when (response.status) {
                    HttpStatusCode.OK -> {
                        LOG.info("Sent document to print")
                        response.receive<JournalpostdistribusjonResponse>()
                    }
                    HttpStatusCode.Unauthorized -> {
                        LOG.error("Failed to send document to print: Unable to authorize")
                        null
                    }
                    else -> {
                        LOG.error("Error while calling distribuerjournalpost: $response")
                        null
                    }
                }

            } catch (e: Exception) {
                LOG.error("Exception while calling distribuerjournalpost: ${e.message}", e)
                null
            }
        }
    }
}
