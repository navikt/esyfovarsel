package no.nav.syfo.consumer.narmesteLeder

import io.ktor.client.call.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.utils.get
import org.slf4j.LoggerFactory

class NarmesteLederConsumer(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val basepath = urlEnv.narmestelederUrl
    private val scope = urlEnv.narmestelederScope
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.SykmeldingerConsumer")

    suspend fun getNarmesteLeder(ansattFnr: String, orgnummer: String): NarmestelederResponse? {
        log.info("Kaller narmesteleder for orgnummer: $orgnummer")
        val requestURL = "$basepath/sykmeldt/narmesteleder?orgnummer=$orgnummer"
        try {
            val token = azureAdTokenConsumer.getToken(scope)
            val response = get(
                requestURL, token,
                hashMapOf(
                    "Sykmeldt-Fnr" to ansattFnr,
                    HttpHeaders.ContentType to ContentType.Application.Json.toString(),
                    HttpHeaders.Accept to ContentType.Application.Json.toString(),
                )
            )

            return when (response.status) {
                HttpStatusCode.OK -> {
                    response.receive<NarmestelederResponse>()
                }
                HttpStatusCode.Unauthorized -> {
                    log.error("Could not get nærmeste leder: Unable to authorize")
                    null
                }
                else -> {
                    log.error("Could not get nærmeste leder: $response")
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Encountered exception during call to narmesteleder: ${e.message}")
            return null
        } catch (e: Error) {
            log.error("Encountered error!!: ${e.message}")
            throw e
        }
    }
}
