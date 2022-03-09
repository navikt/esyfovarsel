package no.nav.syfo.auth

interface TokenConsumer {
    suspend fun getToken(resource: String?): String
}
