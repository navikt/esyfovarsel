package no.nav.syfo.consumer.dkif

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.domain.Kontaktinfo
import no.nav.syfo.consumer.domain.KontaktinfoMapper
import no.nav.syfo.utils.*
import org.slf4j.LoggerFactory
import java.util.*

class DkifConsumer(private val urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()

    fun person(fnr: String): Kontaktinfo? {
        return runBlocking {
            val accessToken = "Bearer ${azureAdTokenConsumer.getToken(urlEnv.dkifScope)}"
            val response: HttpResponse? = try {
                client.get<HttpResponse>(urlEnv.dkifUrl) {
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        append(HttpHeaders.Authorization, accessToken)
                        append(NAV_PERSONIDENT_HEADER, fnr)
                        append(NAV_CALL_ID_HEADER, createCallId())
                    }
                }
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
        private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.dkif.DkifConsumer")

        private fun createCallId(): String {
            val randomUUID = UUID.randomUUID().toString()
            return "esyfovarsel-$randomUUID"
        }
    }
}
