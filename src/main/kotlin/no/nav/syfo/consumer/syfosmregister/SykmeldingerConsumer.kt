package no.nav.syfo.consumer.syfosmregister

import io.ktor.client.call.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.utils.post
import org.slf4j.LoggerFactory
import java.time.LocalDate

class SykmeldingerConsumer(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val basepath = urlEnv.syfosmregisterUrl
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer")
    private val scope = urlEnv.syfosmregisterScope

    suspend fun getSykmeldtStatusPaDato(dato: LocalDate, fnr: String): SykmeldtStatusResponse? {
        val requestURL = "$basepath/api/v2/sykmelding/sykmeldtStatus"
        val requestBody = SykmeldtStatusRequest(fnr, dato)
        val token = azureAdTokenConsumer.getToken(scope)

        val response = post(
            requestURL, requestBody, token,
            hashMapOf(
                HttpHeaders.Accept to ContentType.Application.Json.toString(),
                HttpHeaders.ContentType to ContentType.Application.Json.toString(),
            )
        )

        return when (response.status) {
            HttpStatusCode.OK -> {
                response.receive<SykmeldtStatusResponse>()
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not get sykmeldinger from syfosmregister: Unable to authorize")
                null
            }
            else -> {
                log.error("Could not get sykmeldinger from syfosmregister: $response")
                null
            }
        }
    }
}
