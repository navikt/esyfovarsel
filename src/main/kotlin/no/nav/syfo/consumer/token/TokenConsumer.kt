package no.nav.syfo.consumer.token

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.readText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.ServiceUserSecrets
import no.nav.syfo.logger
import no.nav.syfo.objectMapper

class TokenConsumer(
        val serviceUserSecrets: ServiceUserSecrets,
        val httpClient: HttpClient = HttpClient()
) {
    val LOGGER = logger()

    suspend fun token(): String {
        return fetchToken().access_token
    }

    suspend fun fetchToken(): Token {
        val response = httpClient.get<HttpStatement> {
            url {
                host = "https://security-token-service.nais.preprod.local"
                path("rest/v1/sts/token")
            }
            header("Authorization", serviceUserSecrets.basicAuth)
            accept(ContentType.Application.Json)
        }.execute()

        if (HttpStatusCode.OK == response.status) {
            LOGGER.info("Henting av token, status: ${response.status}")
            return objectMapper.readValue(response.readText())
        } else {
            LOGGER.error("Feil ved henting av token, status: ${response.status}")
            throw RuntimeException("Feil ved henting av token: ${response.status}")
        }
    }
}

data class Token(
        val access_token: String,
        val token_type: String,
        val expires_in: Int
)
