package no.nav.syfo.consumer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusRequest
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusResponse
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

class SykmeldingerConsumer(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()
    private val basepath = urlEnv.syfosmregisterUrl
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.SykmeldingerConsumer")
    private val scope = urlEnv.syfosmregisterScope

    suspend fun getSykmeldtStatusPaDato(dato: LocalDate, fnr: String): SykmeldtStatusResponse? {
        val requestURL = "$basepath/api/v2/sykmelding/sykmeldtStatus"
        val requestBody = SykmeldtStatusRequest(fnr, dato)
        val token = azureAdTokenConsumer.getAzureAdAccessToken(scope)

        val response = client.post<HttpResponse>(requestURL) {
            headers {
                append(HttpHeaders.Accept, ContentType.Application.Json)
                append(HttpHeaders.ContentType, ContentType.Application.Json)
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            body = requestBody
        }

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
