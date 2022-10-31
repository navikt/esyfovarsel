package no.nav.syfo.consumer.syfosmregister

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

class SykmeldingerConsumer(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()
    private val basepath = urlEnv.syfosmregisterUrl
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer")
    private val scope = urlEnv.syfosmregisterScope

    suspend fun getSykmeldingerPaDato(dato: LocalDate, fnr: String): List<SykmeldingDTO>? {
        val requestURL = "$basepath/api/v2/sykmelding/sykmeldinger"
        val token = azureAdTokenConsumer.getToken(scope)

        val response = client.get<HttpResponse>(requestURL) {
            headers {
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.Authorization, "Bearer $token")
                append("fnr", fnr)
            }
            url {
                parameters.append("fom", dato.toString())
                parameters.append("tom", dato.toString())
            }
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                response.receive<List<SykmeldingDTO>>()
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

    suspend fun getSykmeldtStatusPaDato(dato: LocalDate, fnr: String): SykmeldtStatus? {
        val requestURL = "$basepath/api/v2/sykmelding/sykmeldtStatus"
        val token = azureAdTokenConsumer.getToken(scope)

        val response = client.post<HttpResponse>(requestURL) {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            body = SykmeldtStatusRequest(fnr, dato)
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                response.receive<SykmeldtStatus>()
            }

            HttpStatusCode.Unauthorized -> {
                log.error("Could not get sykmeldt status from syfosmregister: Unable to authorize")
                null
            }

            else -> {
                log.error("Could not get sykmeldt status from syfosmregister: $response")
                null
            }
        }
    }
}
