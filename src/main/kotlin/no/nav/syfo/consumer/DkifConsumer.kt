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
import io.ktor.util.*
import no.nav.syfo.Environment
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.consumer.domain.DigitalKontaktinfo
import no.nav.syfo.consumer.domain.DigitalKontaktinfoBolk
import no.nav.syfo.consumer.pdl.APPLICATION_JSON
import org.slf4j.LoggerFactory
import java.lang.RuntimeException
import java.util.UUID.randomUUID

@KtorExperimentalAPI
class DkifConsumer(env: Environment, stsConsumer: StsConsumer) {
    private val client: HttpClient
    private val stsConsumer: StsConsumer
    private val dkifBasepath: String

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
        dkifBasepath = env.dkifUrl
    }

    suspend fun isBrukerReservert(aktorId: String) : DigitalKontaktinfo? {
        val requestUrl = "$dkifBasepath/api/v1/personer/kontaktinformasjon"
        val stsTokenString = "Bearer ${stsConsumer.getToken().access_token}"
        val response: HttpResponse? = try {
            client.get<HttpResponse>(requestUrl) {
                headers {
                    append(HttpHeaders.ContentType, APPLICATION_JSON)
                    append(HttpHeaders.Authorization, stsTokenString)
                    append(NAV_CONSUMER_ID_HEADER, ESYFOVARSEL_CONSUMER_ID)
                    append(NAV_PERSONIDENTER_HEADER, aktorId)
                    append(NAV_CALL_ID_HEADER, createCallId())
                }
            }
        } catch (e: Exception) {
            log.error("Error while calling DKIF: ${e.message}")
            null
        }

        return when (response?.status) {
            HttpStatusCode.OK -> {
                val content = response.receive<DigitalKontaktinfoBolk>()
                extractDataFromContent(content, aktorId)
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not get kontaktinfo from DKIF: Unable to authorize")
                null
            }
            else -> {
                log.error("Could not get kontaktinfo from DKIF: $response")
                null
            }
        }
    }

    companion object {
        private const val NAV_CONSUMER_ID_HEADER = "Nav-Consumer-Id"
        private const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
        private const val ESYFOVARSEL_CONSUMER_ID = "srvesyfovarsel"
        private const val DKIF_IKKE_FUNNET_FEIL = "Ingen kontaktinformasjon er registrert på personen"
        private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.DkifConsumer")
        const val NAV_PERSONIDENTER_HEADER = "Nav-Personidenter"

        private fun createCallId() : String {
            val randomUUID = randomUUID().toString()
            return "esyfovarsel-$randomUUID"
        }

        private fun extractDataFromContent(content: DigitalKontaktinfoBolk, aktorId: String) : DigitalKontaktinfo? {
            val kontaktinfo = content.kontaktinfo?.get(aktorId)
            val feil = content.feil?.get(aktorId)

            return kontaktinfo
                ?: feil?.let {
                    return if (it.melding == DKIF_IKKE_FUNNET_FEIL)
                        DigitalKontaktinfo(personident = aktorId, kanVarsles = false)
                    else
                        throw RuntimeException(it.melding)
                }
                ?: throw RuntimeException("Kontaktinfo is null")
        }
    }
}
