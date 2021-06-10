package no.nav.syfo.auth

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
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
import no.nav.syfo.consumer.pdl.APPLICATION_JSON
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*


@KtorExperimentalAPI
class AzureAdTokenConsumer(env: Environment) {
    private val aadAccessTokenUrl = env.aadAccessTokenUrl
    private val clientId = env.clientId
    private val clientSecret = env.clientSecret
    private val log: Logger = LoggerFactory.getLogger("azureadtokenconsumer")

    private val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    @Volatile
    private var azureAdTokenMap = HashMap<String, AadAccessToken>()

    suspend fun hentAccessToken(resource: String): String? {
        val omToMinutter = Instant.now().plusSeconds(120L)
        val azureAdResponse: AadAccessToken? = azureAdTokenMap.get(resource)

        if (azureAdResponse == null || azureAdResponse.expires_on.isBefore(omToMinutter)) {

            log.info("Henter nytt token fra Azure AD for ressurs {}", resource)

            val response = callAzureAd(resource)

            return when (response?.status) {
                HttpStatusCode.OK -> {
                    val aadAccessToken = response.receive<AadAccessToken>()
                    azureAdTokenMap.put(resource, aadAccessToken)
                    aadAccessToken.access_token
                }
                else -> {
                    log.error("Could not get access_token from Azure AD: $response")
                    null
                }
            }
        } else {
            return azureAdResponse.access_token
        }
    }

    fun callAzureAd(resource: String): HttpResponse? {
        return runBlocking {
            log.info("Henter nytt token fra Azure AD for ressurs {}", resource)

            val requestBody = AadRequest(clientId, resource, "client_credentials", clientSecret)

            try {
                client.post<HttpResponse>(aadAccessTokenUrl) {
                    headers {
                        append(HttpHeaders.ContentType, APPLICATION_JSON)
                    }
                    body = requestBody
                }
            } catch (e: Exception) {
                log.error("[AKTIVITETSKRAV_VARSEL]: Error while calling AAD: ${e.message}")
                null
            }
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AadAccessToken(
    val access_token: String,
    val expires_on: Instant
)

data class AadRequest(
    val client_id: String,
    val resource: String,
    val grant_type: String,
    val client_secret: String
)

