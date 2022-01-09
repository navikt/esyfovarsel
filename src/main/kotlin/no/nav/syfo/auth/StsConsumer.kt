package no.nav.syfo.auth


import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.AuthEnv
import no.nav.syfo.UrlEnv
import no.nav.syfo.utils.httpClient
import java.time.LocalDateTime
import java.util.Base64

open class StsConsumer(urlEnv: UrlEnv, authEnv: AuthEnv) {
    private val username = authEnv.serviceuserUsername
    private val password = authEnv.serviceuserPassword
    private val stsEndpointUrl = "${urlEnv.stsUrl}/rest/v1/sts/token?grant_type=client_credentials&scope=openid"
    private var token: Token? = null
    private val client = httpClient()

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

class LocalStsConsumer(urlEnv: UrlEnv, authEnv: AuthEnv): StsConsumer(urlEnv, authEnv) {
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
