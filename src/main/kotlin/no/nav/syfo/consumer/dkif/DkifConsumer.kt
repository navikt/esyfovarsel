package no.nav.syfo.consumer.dkif

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.TokenConsumer
import no.nav.syfo.consumer.domain.Kontaktinfo
import no.nav.syfo.consumer.domain.KontaktinfoMapper
import no.nav.syfo.utils.get
import org.slf4j.LoggerFactory
import java.util.UUID.randomUUID

class DkifConsumer(private val urlEnv: UrlEnv, private val tokenConsumer: TokenConsumer) {

    fun kontaktinfo(aktorId: String): Kontaktinfo? {
        return runBlocking {
            val token = tokenConsumer.getToken(urlEnv.dkifScope)
            val response: HttpResponse? = try {
                get(
                    urlEnv.dkifUrl, token,
                    hashMapOf(
                        HttpHeaders.ContentType to ContentType.Application.Json.toString(),
                        NAV_CONSUMER_ID_HEADER to ESYFOVARSEL_CONSUMER_ID,
                        NAV_PERSONIDENTER_HEADER to aktorId,
                        NAV_CALL_ID_HEADER to createCallId(),
                    )
                )
            } catch (e: Exception) {
                log.error("Error while calling DKIF: ${e.message}", e)
                null
            }
            when (response?.status) {
                HttpStatusCode.OK -> {
                    val rawJson: String = response.receive()
                    KontaktinfoMapper.map(rawJson, aktorId)
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

    fun person(fnr: String): Kontaktinfo? {
        return runBlocking {
            val token = tokenConsumer.getToken(urlEnv.dkifScope)
            val response: HttpResponse? = try {
                get(
                    urlEnv.dkifUrl, token,
                    hashMapOf(
                        NAV_PERSONIDENT_HEADER to fnr,
                        NAV_CALL_ID_HEADER to createCallId(),
                        HttpHeaders.ContentType to ContentType.Application.Json.toString(),
                    )
                )
            } catch (e: Exception) {
                log.error("Error while calling DKIF: ${e.message}", e)
                null
            }
            when (response?.status) {
                HttpStatusCode.OK -> {
                    val rawJson: String = response.receive()
                    KontaktinfoMapper.mapPerson(rawJson)
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
        private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.dkif.DkifConsumer")
        const val NAV_PERSONIDENTER_HEADER = "Nav-Personidenter"
        const val NAV_PERSONIDENT_HEADER = "Nav-Personident"

        private fun createCallId(): String {
            val randomUUID = randomUUID().toString()
            return "esyfovarsel-$randomUUID"
        }
    }
}
