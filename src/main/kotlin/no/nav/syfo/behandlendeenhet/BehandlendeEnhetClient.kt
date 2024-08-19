package no.nav.syfo.behandlendeenhet

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.behandlendeenhet.domain.BehandlendeEnhet
import no.nav.syfo.utils.NAV_CALL_ID_HEADER
import no.nav.syfo.utils.NAV_PERSONIDENT_HEADER
import no.nav.syfo.utils.createCallId
import no.nav.syfo.utils.httpClientWithRetry
import org.slf4j.LoggerFactory

const val BEHANDLENDEENHET_PATH = "/api/system/v2/personident"

class BehandlendeEnhetClient(
    urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer
) {
    private val httpClient = httpClientWithRetry()
    private val log = LoggerFactory.getLogger(BehandlendeEnhetClient::class.qualifiedName)

    private val behandlendeEnhetUrl = urlEnv.behandlendeEnhetUrl
    private val behandlendeEnhetScope = urlEnv.behandlendeEnhetScope

    suspend fun getBehandlendeEnhet(personIdent: String): BehandlendeEnhet? {
        val token = azureAdTokenConsumer.getToken(behandlendeEnhetScope)

        val response: HttpResponse = try {
            httpClient.get("$behandlendeEnhetUrl$BEHANDLENDEENHET_PATH") {
                header(HttpHeaders.ContentType, "application/json")
                header(HttpHeaders.Authorization, "Bearer $token")
                header(NAV_CALL_ID_HEADER, createCallId())
                header(NAV_PERSONIDENT_HEADER, personIdent)
            }
        } catch (e: Exception) {
            log.error("Error while calling behandlendeenhet: ${e.message}", e)
            return null
        }


        when (response.status) {
            HttpStatusCode.OK -> {
                return response.body<BehandlendeEnhet>()
            }

            HttpStatusCode.Unauthorized -> {
                log.error("Error requesting behandlendeenhet from syfobehandlendeenhet: Unable to authorize")
                return null
            }

            else -> {
                log.error("Error requesting behandlendeenhet from syfobehandlendeenhet: $response")
                return null
            }
        }
    }
}
