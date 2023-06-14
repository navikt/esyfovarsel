package no.nav.syfo.consumer.syfosmregister

import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

        val response = client.get(requestURL) {
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
                response.body<List<SykmeldingDTO>>()
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

        val response = client.post(requestURL) {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(SykmeldtStatusRequest(fnr, dato))
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                response.body<SykmeldtStatus>()
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
