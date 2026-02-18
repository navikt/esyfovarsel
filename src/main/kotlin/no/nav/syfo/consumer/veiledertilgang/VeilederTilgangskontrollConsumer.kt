package no.nav.syfo.consumer.veiledertilgang

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.utils.NAV_CALL_ID_HEADER
import no.nav.syfo.utils.NAV_PERSONIDENT_HEADER
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory

class VeilederTilgangskontrollConsumer(
    urlEnv: UrlEnv,
    private val azureAdTokenConsumer: AzureAdTokenConsumer,
) {
    private val client = httpClient()
    private val basepath = urlEnv.istilgangskontrollUrl
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.veileder.VeilederTilgangskontrollConsumer")
    private val scope = urlEnv.istilgangskontrollScope

    suspend fun hasAccess(
        personIdent: PersonIdent,
        token: String,
        callId: String,
    ): Boolean {
        val requestURL = "$basepath/api/tilgang/navident/person"

        try {
            val onBehalfOfToken =
                azureAdTokenConsumer.getOnBehalfOfToken(
                    resource = scope,
                    token = token,
                ) ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")

            val response =
                client.get(requestURL) {
                    header(HttpHeaders.Authorization, "Bearer $onBehalfOfToken")
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    header(NAV_PERSONIDENT_HEADER, personIdent.value)
                    header(NAV_CALL_ID_HEADER, callId)
                }

            return response.body<Tilgang>().erGodkjent
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                log.warn("Denied veileder access to person: ${e.message}")
            } else {
                log.error("Encountered exception during call to tilgangskontroll: ${e.message}")
            }
            return false
        } catch (e: Exception) {
            log.error("Encountered exception during call to tilgangskontroll: ${e.message}")
            return false
        } catch (e: Error) {
            log.error("Encountered error!!: ${e.message}")
            throw e
        }
    }
}
