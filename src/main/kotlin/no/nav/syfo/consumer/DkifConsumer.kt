package no.nav.syfo.consumer

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.TokenConsumer
import no.nav.syfo.consumer.domain.DigitalKontaktinfo
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory
import java.util.UUID.randomUUID

class DkifConsumer(urlEnv: UrlEnv, private val tokenConsumer: TokenConsumer) {
    private val client = httpClient()
    private val requestUrl = urlEnv.dkifUrl
    private val tokenScope = "api://dev-gcp.team-rocket.digdir-krr-proxy/.default"

    fun kontaktinfo(aktorId: String): DigitalKontaktinfo? {
        return runBlocking {
            val access_token = "Bearer ${tokenConsumer.getToken(tokenScope)}"
            val response: HttpResponse? = try {
                client.get<HttpResponse>(requestUrl) {
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        append(HttpHeaders.Authorization, access_token)
                        append(NAV_CONSUMER_ID_HEADER, ESYFOVARSEL_CONSUMER_ID)
                        append(NAV_PERSONIDENTER_HEADER, aktorId)
                        append(NAV_CALL_ID_HEADER, createCallId())
                    }
                }
            } catch (e: Exception) {
                log.error("Error while calling DKIF: ${e.message}", e)
                null
            }
            when (response?.status) {
                HttpStatusCode.OK -> {
                    response.receive<DigitalKontaktinfo>()
                }
                HttpStatusCode.Unauthorized -> {
                    log.error("Could not get kontaktinfo from DKIF: Unable to authorize")
                    null
                }
                else -> {
                    log.error("Could not get kontaktinfo from DKIF: $response")
                    null
                }
            }
        }
    }

    companion object {
        private const val NAV_CONSUMER_ID_HEADER = "Nav-Consumer-Id"
        private const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
        private const val ESYFOVARSEL_CONSUMER_ID = "srvesyfovarsel"
        private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.DkifConsumer")
        const val NAV_PERSONIDENTER_HEADER = "Nav-Personidenter"

        private fun createCallId(): String {
            val randomUUID = randomUUID().toString()
            return "esyfovarsel-$randomUUID"
        }
    }
}
