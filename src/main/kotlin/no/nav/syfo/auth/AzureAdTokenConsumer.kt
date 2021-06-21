package no.nav.syfo.auth

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.sync.Mutex
import no.nav.syfo.Environment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant


@KtorExperimentalAPI
class AzureAdTokenConsumer(env: Environment) {
    private val aadAccessTokenUrl = env.aadAccessTokenUrl
    private val clientId = env.clientId
    private val clientSecret = env.clientSecret
    private val log: Logger = LoggerFactory.getLogger("azureadtokenconsumer")
    private val mutex = Mutex()

    private val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    @Volatile
    private var tokenMap = HashMap<String, AadAccessTokenMedExpiry>()

    suspend fun getAzureAdAccessToken(resource: String): String {
        val omToMinutter = Instant.now().plusSeconds(120L)
        log.info("Henter nytt token fra Azure AD1")

        log.info(" nytt token fra Azure1, aadAccessTokenUrl: $aadAccessTokenUrl")
        log.info(" nytt token fra Azure1, clientId: $clientId")
        log.info(" nytt token fra Azure1, resource: $resource")
        log.info(" nytt token fra Azure1, clientSecret: $clientSecret")
        log.info(" nytt token fra Azure1, tokenMap: $tokenMap")
        log.info(" nytt token fra Azure1, tokenMap[resource]: $tokenMap[$resource]")

        val resp: AadAccessTokenMedExpiry? = tokenMap.get(resource)

        if (resp == null || resp.expiresOn.isBefore(omToMinutter)) {
            log.info("Henter nytt token fra Azure AD for scope : $resource")

            val response = client.post<HttpResponse>(aadAccessTokenUrl) {
                accept(ContentType.Application.Json)
                method = HttpMethod.Post
                body = FormDataContent(Parameters.build {
                    append("client_id", clientId) //"543ceb1e-eb69-4089-9458-bdec61160afa"
                    append("scope", resource)
                    append("grant_type", "client_credentials")
                    append("client_secret", clientSecret)
                })
            }

            log.info("Status from Azure AD response : $response ")

            if (response.status == HttpStatusCode.OK) {
                tokenMap[resource] = response.receive<AadAccessTokenMedExpiry>()
                log.info("Status from Azure AD is ok : $tokenMap[$resource] ")
            } else {
                log.error("Could not get sykmeldinger from Azure AD: $response")
            }
        }
        return tokenMap[resource]!!.access_token
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AadAccessTokenMedExpiry(
    val access_token: String,
    val expires_in: Int,
    val expiresOn: Instant
)


