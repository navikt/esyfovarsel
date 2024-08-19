package no.nav.syfo.consumer.dkif

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.domain.Kontaktinfo
import no.nav.syfo.consumer.domain.KontaktinfoMapper
import no.nav.syfo.utils.NAV_CALL_ID_HEADER
import no.nav.syfo.utils.NAV_PERSONIDENT_HEADER
import no.nav.syfo.utils.createCallId
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory

class DkifConsumer(private val urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()

    suspend fun person(fnr: String): Kontaktinfo? {
        val accessToken = "Bearer ${azureAdTokenConsumer.getToken(urlEnv.dkifScope)}"
        val response: HttpResponse? = try {
            client.get(urlEnv.dkifUrl) {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    append(HttpHeaders.Authorization, accessToken)
                    append(NAV_PERSONIDENT_HEADER, fnr)
                    append(NAV_CALL_ID_HEADER, createCallId())
                }
            }
        } catch (e: Exception) {
            log.error("Error while calling DKIF: ${e.message}", e)
            return null
        }
        when (response?.status) {
            HttpStatusCode.OK -> {
                val rawJson: String = response.body()
                return KontaktinfoMapper.mapPerson(rawJson)
            }

            HttpStatusCode.Unauthorized -> {
                log.error("Could not get kontaktinfo from DKIF: Unable to authorize")
                return null
            }

            else -> {
                log.error("Could not get kontaktinfo from DKIF: $response")
                return null
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.dkif.DkifConsumer")
    }
}
