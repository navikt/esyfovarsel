package no.nav.syfo.auth

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.CommonEnvironment
import java.time.LocalDateTime
import java.util.Base64

open class StsConsumer(env: CommonEnvironment) {
    private val username = env.serviceuserUsername
    private val password = env.serviceuserPassword
    private val stsEndpointUrl = "${env.stsUrl}/rest/v1/sts/token?grant_type=client_credentials&scope=openid"
    private var token: Token? = null

    private val client = HttpClient(CIO) {
        expectSuccess = false
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    fun isValidToken(token: Token?) : Boolean {
        return token?.expiresAt?.isAfter(LocalDateTime.now()) ?: false
    }

    open suspend fun getToken(): Token {
        if(isValidToken(token)) {
            return token!!
        }

        token = client.post<Token>(stsEndpointUrl) {
            headers{
                append(HttpHeaders.Authorization, encodeCredentials(username, password))
            }
        }

        return token!!
    }
}

class LocalStsConsumer(env: CommonEnvironment): StsConsumer(env) {
    override suspend fun getToken(): Token = Token(
        "access_token_string",
        "access_token",
        3600
    )
}

fun encodeCredentials(username: String, password: String): String {
    return "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
}

data class Token(val access_token: String, val token_type: String, val expires_in: Int) {
    var expiresAt: LocalDateTime = LocalDateTime.now().plusSeconds(expires_in - 10L)
}
