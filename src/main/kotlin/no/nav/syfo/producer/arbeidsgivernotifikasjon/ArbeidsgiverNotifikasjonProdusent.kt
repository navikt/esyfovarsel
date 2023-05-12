package no.nav.syfo.producer.arbeidsgivernotifikasjon

import io.ktor.client.call.receive
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverDeleteNotifikasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjon
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory

open class ArbeidsgiverNotifikasjonProdusent(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()
    private val arbeidsgiverNotifikasjonProdusentBasepath = urlEnv.arbeidsgiverNotifikasjonProdusentApiUrl
    private val log = LoggerFactory.getLogger(ArbeidsgiverNotifikasjonProdusent::class.qualifiedName)
    private val scope = urlEnv.arbeidsgiverNotifikasjonProdusentApiScope

    open fun createNewNotificationForArbeidsgiver(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjon): String? {
        log.info("About to send new notification with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api")
        val response: HttpResponse = callArbeidsgiverNotifikasjonProdusent(
            CREATE_NOTIFICATION_AG_MUTATION,
            VariablesCreate(
                arbeidsgiverNotifikasjon.varselId,
                arbeidsgiverNotifikasjon.virksomhetsnummer,
                arbeidsgiverNotifikasjon.url,
                arbeidsgiverNotifikasjon.narmesteLederFnr,
                arbeidsgiverNotifikasjon.ansattFnr,
                arbeidsgiverNotifikasjon.merkelapp,
                arbeidsgiverNotifikasjon.messageText,
                arbeidsgiverNotifikasjon.narmesteLederEpostadresse,
                arbeidsgiverNotifikasjon.emailTitle,
                arbeidsgiverNotifikasjon.emailBody,
                EpostSendevinduTypes.LOEPENDE,
                arbeidsgiverNotifikasjon.hardDeleteDate.toString()
            )
        )

        if (response.status == HttpStatusCode.OK) {
            val beskjed = runBlocking { response.receive<OpprettNyBeskjedArbeidsgiverNotifikasjonResponse>() }
            if (beskjed.data !== null) {
                if (beskjed.data.nyBeskjed.__typename?.let {
                        OpprettNyBeskjedArbeidsgiverNotifikasjonMutationStatus.NY_BESKJED_VELLYKKET.status.equals(
                            it
                        )
                    } == true) {
                    log.info("Have send new notification with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api")
                    return beskjed.data.nyBeskjed.id
                } else {
                    throw RuntimeException("Could not send notification because of error: ${beskjed.data.nyBeskjed.feilmelding}")
                }
            } else {
                val errors =
                    runBlocking { response.receive<OpprettNyBeskjedArbeidsgiverNotifikasjonErrorResponse>().errors }
                throw RuntimeException("Could not send send notification to arbeidsgiver. because of error: ${errors[0].message}, data was null: $beskjed")
            }
        } else {
            throw RuntimeException("Could not send send notification to arbeidsgiver. Status code: ${response.status.value}. Response: $response")
        }
    }

    open fun deleteNotifikasjonForArbeidsgiver(arbeidsgiverDeleteNotifikasjon: ArbeidsgiverDeleteNotifikasjon) {
        log.info("About to delete notification with uuid ${arbeidsgiverDeleteNotifikasjon.eksternReferanse} and merkelapp ${arbeidsgiverDeleteNotifikasjon.merkelapp} from ag-notifikasjon-produsent-api")
        callArbeidsgiverNotifikasjonProdusent(
            DELETE_NOTIFICATION_AG_MUTATION,
            VariablesDelete(
                arbeidsgiverDeleteNotifikasjon.merkelapp,
                arbeidsgiverDeleteNotifikasjon.eksternReferanse
            )
        )
    }

    private fun callArbeidsgiverNotifikasjonProdusent(
        mutationPath: String,
        variables: Variables,
    ): HttpResponse {
        return runBlocking {
            val token = azureAdTokenConsumer.getToken(scope)
            val graphQuery =
                this::class.java.getResource("$MUTATION_PATH_PREFIX/$mutationPath")?.readText()?.replace("[\n\r]", "")

            val requestBody = graphQuery?.let { NotificationAgRequest(it, variables) }

            try {
                client.post<HttpResponse>(arbeidsgiverNotifikasjonProdusentBasepath) {
                    headers {
                        append(HttpHeaders.Accept, ContentType.Application.Json)
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                    if (requestBody != null) {
                        body = requestBody
                    }
                }
            } catch (e: Exception) {
                log.error("Error while calling ag-notifikasjon-produsent-api ($mutationPath): ${e.message}", e)
                throw RuntimeException(
                    "Error while calling ag-notifikasjon-produsent-api ($mutationPath): ${e.message}",
                    e
                )
            }
        }
    }
}
