package no.nav.syfo.auth

interface ITokenConsumer {
    suspend fun getToken(scope: String): String

    suspend fun getOnBehalfOfToken(
        scope: String,
        token: String,
    ): String?
}
