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
import no.nav.syfo.AuthEnv
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import java.time.Instant

class AzureAdTokenConsumer(authEnv: AuthEnv) : TokenConsumer {
    private val aadAccessTokenUrl = authEnv.aadAccessTokenUrl
    private val clientId = authEnv.clientId
    private val clientSecret = authEnv.clientSecret
    private val log: Logger = LoggerFactory.getLogger("AzureAdTokenConsumer")

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            expectSuccess = false
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

    override suspend fun getToken(resource: String?): String {
        val omToMinutter = Instant.now().plusSeconds(120L)

        val token: AzureAdAccessToken? = tokenMap.get(resource)

        if (token == null || token.issuedOn!!.plusSeconds(token.expires_in).isBefore(omToMinutter)) {
            log.info("Henter nytt token fra Azure AD for scope : $resource")

            val response = httpClientWithProxy.post<HttpResponse>(aadAccessTokenUrl) {
                accept(ContentType.Application.Json)

                body = FormDataContent(
                    Parameters.build {
                        append("client_id", clientId)
                        append("scope", resource!!)
                        append("grant_type", "client_credentials")
                        append("client_secret", clientSecret)
                    }
                )
            }
            if (response.status == HttpStatusCode.OK) {
                tokenMap[resource!!] = response.receive()
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
    val expires_in: Long,
    val issuedOn: Instant? = Instant.now()
)
