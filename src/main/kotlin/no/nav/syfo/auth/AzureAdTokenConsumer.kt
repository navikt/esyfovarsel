package no.nav.syfo.auth

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import no.nav.syfo.Environment
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import java.time.Instant


@KtorExperimentalAPI
class AzureAdTokenConsumer(env: Environment) {
    private val aadAccessTokenUrl = env.aadAccessTokenUrl
    private val clientId = env.clientId
    private val clientSecret = env.clientSecret
    private val log: Logger = LoggerFactory.getLogger("AzureAdTokenConsumer")

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    @Volatile
    private var tokenMap = HashMap<String, AzureAdAccessToken>()

    suspend fun getAzureAdAccessToken(resource: String): String {
        val omToMinutter = Instant.now().plusSeconds(120L)

        val token: AzureAdAccessToken? = tokenMap.get(resource)

        if (token == null || Instant.now().plusSeconds(token.expires_in.toLong()).isBefore(omToMinutter)) {
            log.info("Henter nytt token fra Azure AD for scope : $resource")

            val response = httpClientWithProxy.post<HttpResponse>(aadAccessTokenUrl) {
                accept(ContentType.Application.Json)

                body = FormDataContent(Parameters.build {
                    append("client_id", clientId)
                    append("scope", resource)
                    append("grant_type", "client_credentials")
                    append("client_secret", clientSecret)
                })
            }
            if (response.status == HttpStatusCode.OK) {
                tokenMap[resource] = response.receive()
            } else {
                log.error("Could not get token from Azure AD: $response")
            }
        }
        return tokenMap[resource]!!.access_token
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AzureAdAccessToken(
    val access_token: String,
    val expires_in: Int
)
