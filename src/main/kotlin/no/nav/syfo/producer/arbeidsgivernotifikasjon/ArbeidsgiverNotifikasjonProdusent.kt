package no.nav.syfo.producer.arbeidsgivernotifikasjon

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_MERKELAPP
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjon
import no.nav.syfo.utils.post
import org.slf4j.LoggerFactory

open class ArbeidsgiverNotifikasjonProdusent(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val arbeidsgiverNotifikasjonProdusentBasepath = urlEnv.arbeidsgiverNotifikasjonProdusentApiUrl
    private val log = LoggerFactory.getLogger("no.nav.syfo.consumer.AgNotifikasjonProdusentConsumer")
    private val scope = urlEnv.arbeidsgiverNotifikasjonProdusentApiScope

    open fun createNewNotificationForArbeidsgiver(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjon): String? {
        log.info("About to send new notification with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api")
        val response: HttpResponse? = callArbeidsgiverNotifikasjonProdusent(arbeidsgiverNotifikasjon)
        return when (response?.status) {
            HttpStatusCode.OK -> {
                val beskjed = runBlocking { response.receive<OpprettNyBeskjedArbeidsgiverNotifikasjonResponse>() }
                return if (beskjed.data !== null) {
                    if (beskjed.data.nyBeskjed.__typename?.let { OpprettNyBeskjedArbeidsgiverNotifikasjonMutationStatus.NY_BESKJED_VELLYKKET.status.equals(it) } == true) {
                        log.info("Have send new notification with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api")
                        return beskjed.data.nyBeskjed.id
                    } else {
                        log.error("Could not send notification because of error: ${beskjed.data.nyBeskjed.feilmelding}")
                        null
                    }
                } else {
                    val errors = runBlocking { response.receive<OpprettNyBeskjedArbeidsgiverNotifikasjonErrorResponse>().errors }
                    log.error("Could not send notification because of error: ${errors[0].message}, data was null: $beskjed")
                    null
                }
            }
            HttpStatusCode.NoContent -> {
                log.error("Could not send notification: No content found in the response body")
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Could not send notification: Unable to authorize")
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
                arbeidsgiverNotifikasjon.narmesteLederFnr,
                arbeidsgiverNotifikasjon.ansattFnr,
                ARBEIDSGIVERNOTIFIKASJON_MERKELAPP,
                arbeidsgiverNotifikasjon.messageText,
                arbeidsgiverNotifikasjon.narmesteLederEpostadresse,
                arbeidsgiverNotifikasjon.emailTitle,
                arbeidsgiverNotifikasjon.emailBody,
                EpostSendevinduTypes.LOEPENDE,
                arbeidsgiverNotifikasjon.hardDeleteDate.toString()
            )
            val requestBody = CreateNewNotificationAgRequest(graphQuery, variables)

            try {
                post(
                    arbeidsgiverNotifikasjonProdusentBasepath, requestBody, token,
                    hashMapOf(
                        HttpHeaders.Accept to ContentType.Application.Json.toString(),
                        HttpHeaders.ContentType to ContentType.Application.Json.toString(),
                    )
                )
            } catch (e: Exception) {
                log.error("Error while calling ag-notifikasjon-produsent-api ($CREATE_NOTIFICATION_AG_MUTATION): ${e.message}", e)
                null
            }
        }
    }
}
