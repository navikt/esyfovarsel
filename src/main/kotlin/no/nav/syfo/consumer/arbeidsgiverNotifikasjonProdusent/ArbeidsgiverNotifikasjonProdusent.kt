package no.nav.syfo.consumer.arbeidsgiverNotifikasjonProdusent

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
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.kafka.dinesykmeldte.domain.ArbeidsgiverNotifikasjon
import org.slf4j.LoggerFactory

open class ArbeidsgiverNotifikasjonProdusent(urlEnv: UrlEnv, azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client: HttpClient
    private val azureAdTokenConsumer: AzureAdTokenConsumer
    private val arbeidsgiverNotifikasjonProdusentBasepath: String
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.AgNotifikasjonProdusentConsumer")
    private val scope = urlEnv.arbeidsgiverNotifikasjonProdusentApiScope

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
        arbeidsgiverNotifikasjonProdusentBasepath = urlEnv.arbeidsgiverNotifikasjonProdusentApiUrl
    }

    open fun createNewNotificationForArbeidsgiver(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjon): String? {
        val response: HttpResponse? = callArbeidsgiverNotifikasjonProdusent(arbeidsgiverNotifikasjon)

        return when (response?.status) {
            HttpStatusCode.OK -> {
                val beskjed = runBlocking { response.receive<OpprettNyBeskjedArbeidsgiverNotifikasjonResponse>() }
                return if (beskjed.data !== null) {
                    log.info("Have send new notificationt with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api")
                    beskjed.data.nyBeskjed.id
                } else {
                    log.error("Could not post notification, data is null: $beskjed")
                    val errors = runBlocking { response.receive<OpprettNyBeskjedArbeidsgiverNotifikasjonErrorResponse>().errors }
                    log.error("Could not post notification because of errors: $errors")
                    null
                }
            }
            HttpStatusCode.NoContent -> {
                log.error("Could not post notification: No content found in the response body")
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not post notification: Unable to authorize")
                return null
            }
            else -> {
                log.error("Could not send send notification to arbeidsgiver")
                return null
            }
        }
    }

    private fun callArbeidsgiverNotifikasjonProdusent(
        arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjon,
    ): HttpResponse? {
        return runBlocking {
            val token = azureAdTokenConsumer.getToken(scope)
            val graphQuery = this::class.java.getResource("$MUTATION_PATH_PREFIX/$CREATE_NOTIFICATION_AG_MUTATION").readText().replace("[\n\r]", "")

            val variables = Variables(
                arbeidsgiverNotifikasjon.varselId,
                arbeidsgiverNotifikasjon.virksomhetsnummer,
                arbeidsgiverNotifikasjon.url,
                arbeidsgiverNotifikasjon.naermesteLederFnr,
                arbeidsgiverNotifikasjon.ansattFnr,
                MERKELAPP,
                arbeidsgiverNotifikasjon.messageText,
                arbeidsgiverNotifikasjon.narmesteLederEpostadresse,
                arbeidsgiverNotifikasjon.emailTitle,
                arbeidsgiverNotifikasjon.emailBody,
                EpostSendevinduTypes.LOEPENDE,
            )
            val requestBody = CreateNewNotificationAgRequest(graphQuery, variables)

            try {
                client.post<HttpResponse>(arbeidsgiverNotifikasjonProdusentBasepath) {
                    headers {
                        append(HttpHeaders.Accept, ContentType.Application.Json)
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                    body = requestBody
                }
            } catch (e: Exception) {
                log.error("Error while calling ag-notifikasjon-produsent-api ($CREATE_NOTIFICATION_AG_MUTATION): ${e.message}", e)
                null
            }
        }
    }
}
