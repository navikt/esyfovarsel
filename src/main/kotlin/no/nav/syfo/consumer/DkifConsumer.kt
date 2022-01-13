package no.nav.syfo.consumer

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.consumer.domain.DigitalKontaktinfo
import no.nav.syfo.consumer.domain.DigitalKontaktinfoBolk
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory
import java.util.UUID.randomUUID

class DkifConsumer(urlEnv: UrlEnv, private val stsConsumer: StsConsumer) {
    private val client = httpClient()
    private val dkifBasepath = urlEnv.dkifUrl

    fun kontaktinfo(aktorId: String): DigitalKontaktinfo? {
        val requestUrl = "$dkifBasepath/api/v1/personer/kontaktinformasjon"
        log.info("DKIF: $aktorId|$dkifBasepath")
        return runBlocking {
            val stsTokenString = "Bearer ${stsConsumer.getToken().access_token}"
            val response: HttpResponse? = try {
                client.get<HttpResponse>(requestUrl) {
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        append(HttpHeaders.Authorization, stsTokenString)
                        append(NAV_CONSUMER_ID_HEADER, ESYFOVARSEL_CONSUMER_ID)
                        append(NAV_PERSONIDENTER_HEADER, aktorId)
                        append(NAV_CALL_ID_HEADER, createCallId())
                    }
                }
            } catch (e: Exception) {
                log.error("Error while calling DKIF: ${e.message}", e)
                null
            }
            log.info("DKIF END")
            when (response?.status) {
                HttpStatusCode.OK -> {
                    val content = response.receive<DigitalKontaktinfoBolk>()
                    extractDataFromContent(content, aktorId)
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
        private const val DKIF_IKKE_FUNNET_FEIL = "Ingen kontaktinformasjon er registrert på personen"
        private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.DkifConsumer")
        const val NAV_PERSONIDENTER_HEADER = "Nav-Personidenter"

        private fun createCallId(): String {
            val randomUUID = randomUUID().toString()
            return "esyfovarsel-$randomUUID"
        }

        private fun extractDataFromContent(content: DigitalKontaktinfoBolk, aktorId: String): DigitalKontaktinfo? {
            val kontaktinfo = content.kontaktinfo?.get(aktorId)
            val feil = content.feil?.get(aktorId)

            return kontaktinfo
                ?: feil?.let {
                    return if (it.melding == DKIF_IKKE_FUNNET_FEIL)
                        DigitalKontaktinfo(personident = aktorId, kanVarsles = false)
                    else
                        throw RuntimeException(it.melding)
                }
                ?: throw RuntimeException("Kontaktinfo is null")
        }
    }
}
