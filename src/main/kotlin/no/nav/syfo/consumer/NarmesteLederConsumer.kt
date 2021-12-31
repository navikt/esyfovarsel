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
import no.nav.syfo.AppEnvironment
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.narmesteLeder.NarmestelederResponse
import org.slf4j.LoggerFactory

class NarmesteLederConsumer(env: AppEnvironment, azureAdTokenConsumer: AzureAdTokenConsumer) {

    private val client: HttpClient
    private val azureAdTokenConsumer: AzureAdTokenConsumer
    private val basepath: String
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.SykmeldingerConsumer")
    private val scope = env.narmestelederScope

    init {
        client = HttpClient(CIO) {
            expectSuccess = false
            install(JsonFeature) {
                serializer = JacksonSerializer {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
        }
        this.azureAdTokenConsumer = azureAdTokenConsumer
        basepath = env.narmestelederUrl
    }

    suspend fun getNarmesteLeder(ansattFnr: String, orgnummer: String): NarmestelederResponse? {
        val requestURL = "$basepath/sykmeldt/narmesteleder?orgnummer=$orgnummer"
        val token = azureAdTokenConsumer.getAzureAdAccessToken(scope)

        val response = client.post<HttpResponse>(requestURL) {
            headers {
                append(HttpHeaders.Accept, ContentType.Application.Json)
                append(HttpHeaders.ContentType, ContentType.Application.Json)
                append(HttpHeaders.Authorization, "Bearer $token")
                append("Sykmeldt-Fnr", ansattFnr)
            }
        }

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
    }
}
