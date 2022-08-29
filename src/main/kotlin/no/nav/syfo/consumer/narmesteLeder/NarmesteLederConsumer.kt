package no.nav.syfo.consumer.narmesteLeder

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import org.slf4j.LoggerFactory

class NarmesteLederConsumer(urlEnv: UrlEnv, azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client: HttpClient
    private val azureAdTokenConsumer: AzureAdTokenConsumer
    private val basepath: String
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.NarmesteLederConsumer")
    private val scope = urlEnv.narmestelederScope

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
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
        }
        this.azureAdTokenConsumer = azureAdTokenConsumer
        basepath = urlEnv.narmestelederUrl
    }

    suspend fun getNarmesteLeder(ansattFnr: String, orgnummer: String): NarmestelederResponse? {
        log.info("Kaller narmesteleder for orgnummer: $orgnummer")
        val requestURL = "$basepath/sykmeldt/narmesteleder?orgnummer=$orgnummer"
        try {
            val token = azureAdTokenConsumer.getToken(scope)
            val response = client.get<HttpResponse>(requestURL) {
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
        } catch (e: Exception) {
            log.error("Encountered exception during call to narmesteleder: ${e.message}")
            return null
        } catch (e: Error) {
            log.error("Encountered error!!: ${e.message}")
            throw e
        }
    }
}
