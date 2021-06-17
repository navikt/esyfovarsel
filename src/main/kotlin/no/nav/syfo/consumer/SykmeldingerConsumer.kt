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
import io.ktor.util.*
import no.nav.syfo.Environment
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusRequest
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusResponse
import org.slf4j.LoggerFactory
import java.time.LocalDate

@KtorExperimentalAPI
class SykmeldingerConsumer(env: Environment, azureAdTokenConsumer: AzureAdTokenConsumer) {

    private val client: HttpClient
    private val azureAdTokenConsumer: AzureAdTokenConsumer
    private val basepath: String
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.SyfosyketilfelleConsumer")
    private val scope = env.syfosmregisterScope

    init {
        client = HttpClient(CIO) {
            install(JsonFeature) {
                serializer = JacksonSerializer {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
        }
        this.azureAdTokenConsumer = azureAdTokenConsumer
        basepath = env.syfosmregisterUrl
    }

    suspend fun getSykmeldtStatusPaDato(dato: LocalDate, fnr: String): SykmeldtStatusResponse? {
        val requestURL = "$basepath/api/v2/sykmelding/sykmeldtStatus"
        val requestBody = SykmeldtStatusRequest(fnr, dato)
        val token = azureAdTokenConsumer.getAzureAdAccessToken(scope)

        log.info("[AKTIVITETSKRAV_VARSEL]: Syfosmregister requestURL: [$requestURL]")

        val response = client.post<HttpResponse>(requestURL) {
            headers {
                append(HttpHeaders.Accept, ContentType.Application.Json)
                append(HttpHeaders.ContentType, ContentType.Application.Json)
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            body = requestBody
        }

        log.info("[AKTIVITETSKRAV_VARSEL]: SyfosmregisterResponse, response: [$response]")
        return when (response.status) {
            HttpStatusCode.OK -> {
                log.info("[AKTIVITETSKRAV_VARSEL]: SyfosmregisterResponse, response: [$response]")
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
