package no.nav.syfo.consumer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.Environment
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.consumer.domain.Oppfolgingstilfelle
import org.slf4j.LoggerFactory

@KtorExperimentalAPI
class SyfosyketilfelleConsumer(env: Environment, stsConsumer: StsConsumer) {
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
        basepath = env.syfosyketilfelleUrl
    }

    suspend fun getOppfolgingstilfelle(aktorId: String, orgnr: String) : Oppfolgingstilfelle? {
        val requestURL = "$basepath/kafka/oppfolgingstilfelle/beregn/$aktorId/$orgnr"
        val stsToken = stsConsumer.getToken()
        val bearerTokenString = "Bearer ${stsToken.access_token}"

        val response = client.get<HttpResponse>(requestURL) {
            headers {
                append(HttpHeaders.Authorization, bearerTokenString)
                append(HttpHeaders.Accept, "application/json")
            }
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                response.receive<Oppfolgingstilfelle>()
            }
            HttpStatusCode.NoContent -> {
                log.error("Could not get oppfolgingstilfelle: No content found in the response body")
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not get oppfolgingstilfelle: Unable to authorize")
                null
            }
            else -> {
                log.error("Could not get oppfolgingstilfelle: $response")
                null
            }
        }
    }

}