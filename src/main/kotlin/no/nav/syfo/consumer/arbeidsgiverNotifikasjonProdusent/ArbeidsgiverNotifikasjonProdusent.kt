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
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederConsumer
import org.slf4j.LoggerFactory

open class ArbeidsgiverNotifikasjonProdusent(urlEnv: UrlEnv, azureAdTokenConsumer: AzureAdTokenConsumer, narmesteLederConsumer: NarmesteLederConsumer) {
    private val client: HttpClient
    private val azureAdTokenConsumer: AzureAdTokenConsumer
    private val narmesteLederConsumer: NarmesteLederConsumer
    private val arbeidsgiverNotifikasjonProdusentBasepath: String
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.AgNotifikasjonProdusentConsumer")
    private val scope = urlEnv.arbeidsgiverNotifikasjonProdusentApiScope
    private val dineSykmeldteUrl = urlEnv.baseUrlDineSykmeldte
    private val EMAIL_BODY = EMAIL_BODY_START + dineSykmeldteUrl + EMAIL_BODY_END

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
        this.narmesteLederConsumer = narmesteLederConsumer
        arbeidsgiverNotifikasjonProdusentBasepath = urlEnv.arbeidsgiverNotifikasjonProdusentApiUrl
    }

    open fun createNewNotificationForArbeidsgiver(varselId: String, virksomhetsnummer: String, ansattFnr: String): String? {
        val narmesteLederRelasjon = runBlocking { narmesteLederConsumer.getNarmesteLeder(ansattFnr, virksomhetsnummer)?.narmesteLederRelasjon }
        val narmesteLederFnr = narmesteLederRelasjon?.narmesteLederFnr
        val narmesteLederEpostadresse = narmesteLederRelasjon?.narmesteLederEpost

        if (narmesteLederFnr != null && narmesteLederEpostadresse !== null) {
            val response: HttpResponse? = callArbeidsgiverNotifikasjonProdusent(varselId, virksomhetsnummer, narmesteLederFnr, ansattFnr, narmesteLederEpostadresse)
            return when (response?.status) {
                HttpStatusCode.OK -> {
                    val beskjed = runBlocking { response.receive<OpprettNyBeskjedArbeidsgiverNotifikasjonResponse>() }
                    return if (beskjed.data !== null) {
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
                    log.error("Could get nærmeste leder data")
                    return null
                }
            }
        }
        log.error("Could not post notification: Unable to authorize")
        return null
    }

    private fun callArbeidsgiverNotifikasjonProdusent(
        varselId: String,
        virksomhetsnummer: String,
        naermesteLederFnr: String,
        ansattFnr: String,
        narmesteLederEpostadresse: String
    ): HttpResponse? {
        return runBlocking {
            val token = azureAdTokenConsumer.getToken(scope)
            val graphQuery = this::class.java.getResource("$MUTATION_PATH_PREFIX/$CREATE_NOTIFICATION_AG_MUTATION").readText().replace("[\n\r]", "")
            val variables = Variables(
                varselId,
                virksomhetsnummer,
                dineSykmeldteUrl,
                naermesteLederFnr,
                ansattFnr,
                MERKELAPP,
                MESSAGE_TEXT,
                narmesteLederEpostadresse,
                EMAIL_TITLE,
                EMAIL_BODY,
                EpostSendevinduTypes.LOEPENDE
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
