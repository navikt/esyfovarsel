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
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusRequest
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatusResponse
import org.slf4j.LoggerFactory
import java.time.LocalDate

@KtorExperimentalAPI
class SykmeldingerConsumer(env: Environment, stsConsumer: StsConsumer, azureAdTokenConsumer: AzureAdTokenConsumer) {

    private val client: HttpClient
    private val stsConsumer: StsConsumer
    private val azureAdTokenConsumer: AzureAdTokenConsumer
    private val basepath: String
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.SyfosyketilfelleConsumer")

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
        this.stsConsumer = stsConsumer
        this.azureAdTokenConsumer = azureAdTokenConsumer
        basepath = env.syfosmregisterUrl
    }

    suspend fun getSykmeldingerForVarslingDato(dato: LocalDate, fnr: String): SykmeldtStatusResponse? {
        val requestURL = "$basepath/api/v1/docs/index.html#/default/sykmeldtStatus"
        val requestBody = SykmeldtStatusRequest(fnr, dato)
        val accessToken = azureAdTokenConsumer.hentAccessToken(fnr)

        log.info("[AKTIVITETSKRAV_VARSEL]: Syfosmregister requestURL: [$requestURL]")

        val response = client.post<HttpResponse>(requestURL) {
            headers {
                append(HttpHeaders.Authorization, accessToken)
                append(HttpHeaders.Accept, ContentType.Application.Json)
                append(HttpHeaders.ContentType, ContentType.Application.Json)
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
