package no.nav.syfo.auth

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.syfo.Environment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.collections.set


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

    private val mutex = Mutex()

    @Volatile
    private var tokenMap = HashMap<String, AadAccessToken>()

    suspend fun hentAccessToken(resource: String): String {
        val omToMinutter = Instant.now().plusSeconds(120L)

        log.info("[AKTIVITETSKRAV_VARSEL]: aadAccessTokenUrl:  $aadAccessTokenUrl")
        log.info("[AKTIVITETSKRAV_VARSEL]: clientId:  $clientId")
        log.info("[AKTIVITETSKRAV_VARSEL]: clientSecret:  $clientSecret")

        return mutex.withLock {
            (tokenMap[resource]
                ?.takeUnless { it.expires_on.isBefore(omToMinutter) }
                ?: run {
                    log.debug("Henter nytt token fra Azure AD")
                    val response: AadAccessToken = client.post(aadAccessTokenUrl) {
                        accept(ContentType.Application.Json)
                        method = HttpMethod.Post
                        body = FormDataContent(Parameters.build {
                            append("client_id", clientId)
                            append("resource", resource)
                            append("grant_type", "client_credentials")
                            append("client_secret", clientSecret)
                        })
                    }
                    tokenMap[resource] = response
                    log.debug("Har hentet accesstoken")
                    return@run response
                }).access_token
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AadAccessToken(
    val access_token: String,
    val expires_on: Instant
)
