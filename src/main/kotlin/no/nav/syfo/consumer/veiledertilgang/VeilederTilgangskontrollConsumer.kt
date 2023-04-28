package no.nav.syfo.consumer.veiledertilgang

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.utils.*
import org.slf4j.LoggerFactory

class VeilederTilgangskontrollConsumer(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()
    private val basepath = urlEnv.syfoTilgangskontrollUrl
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.veileder.VeilederTilgangskontrollConsumer")
    private val scope = urlEnv.syfoTilgangskontrollScope

    suspend fun hasAccess(personIdent: PersonIdent, token: String, callId: String): Boolean {
        val requestURL = "$basepath/syfo-tilgangskontroll/api/tilgang/navident/person"

        try {
            val onBehalfOfToken = azureAdTokenConsumer.getOnBehalfOfToken(
                resource = scope,
                token = token,
            ) ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")

            val response = client.get<HttpResponse>(requestURL) {
                header(HttpHeaders.Authorization, "Bearer $onBehalfOfToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                header(NAV_CALL_ID_HEADER, callId)
            }

            return response.receive<Tilgang>().harTilgang
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