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
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.consumer.syfosmregister.SyfosmregisterResponse
import org.slf4j.LoggerFactory
import java.time.LocalDate

@KtorExperimentalAPI
class SykmeldingerConsumer(env: Environment, stsConsumer: StsConsumer)  {

    private val client: HttpClient
    private val stsConsumer: StsConsumer
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
        basepath = env.syfosmregisterUrl
    }

    suspend fun getSykmeldingerForVarslingDato(dato: LocalDate, fnr: String): List<SyfosmregisterResponse>? {
        val datoString = dato.toString()
        val requestURL = "$basepath/api/v1/internal/sykmeldinger/?fom=/$datoString/?tom=/$datoString"//?include=SENDT/
        log.info("[AKTIVITETSKRAV_VARSEL]: Syfosmregister requestURL: [$requestURL]")
        val stsToken = stsConsumer.getToken()
        val bearerTokenString = "Bearer ${stsToken.access_token}"

        val response = client.get<HttpResponse>(requestURL) {
            headers {
                append(HttpHeaders.Authorization, bearerTokenString)
                append(HttpHeaders.Accept, "application/json")
                append("fnr", fnr)
            }
        }
        log.info("[AKTIVITETSKRAV_VARSEL]: SyfosmregisterResponse, response: [$response]")
        return when (response.status) {
            HttpStatusCode.OK -> {
                log.info("[AKTIVITETSKRAV_VARSEL]: SyfosmregisterResponse, response: [$response]")
                response.receive<List<SyfosmregisterResponse>>()
            }
            HttpStatusCode.NoContent -> {
                log.error("Could not get sykmeldinger from syfosmregister: No content found in the response body")
                null
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
