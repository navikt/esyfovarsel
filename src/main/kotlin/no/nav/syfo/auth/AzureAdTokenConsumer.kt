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
import kotlinx.coroutines.sync.withLock
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
        return mutex.withLock {
            (tokenMap[resource]
                ?.takeUnless { it.expiresOn.isBefore(omToMinutter) }
                ?: run {
                    log.info("Henter nytt token fra Azure AD")
                    val response: AadAccessTokenMedExpiry = client.post(aadAccessTokenUrl) {
                        accept(ContentType.Application.Json)
                        method = HttpMethod.Post
                        body = FormDataContent(Parameters.build {
                            append("client_id", clientId)
                            append("scope", resource)
                            append("grant_type", "client_credentials")
                            append("client_secret", clientSecret)
                        })
                    }
                    val tokenMedExpiry = AadAccessTokenMedExpiry(
                        access_token = response.access_token,
                        expires_in = response.expires_in,
                        expiresOn = Instant.now().plusSeconds(response.expires_in.toLong())
                    )
                    tokenMap[resource] = tokenMedExpiry
                    log.debug("Har hentet accesstoken")
                    return@run tokenMedExpiry
                }).access_token
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AadAccessTokenMedExpiry(
    val access_token: String,
    val expires_in: Int,
    val expiresOn: Instant
)


