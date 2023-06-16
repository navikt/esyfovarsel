package no.nav.syfo.producer.arbeidsgivernotifikasjon

import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import no.nav.syfo.producer.arbeidsgivernotifikasjon.response.nybeskjed.NyBeskjedErrorResponse
import no.nav.syfo.producer.arbeidsgivernotifikasjon.response.nybeskjed.NyBeskjedMutationStatus.NY_BESKJED_VELLYKKET
import no.nav.syfo.producer.arbeidsgivernotifikasjon.response.nybeskjed.NyBeskjedResponse
import no.nav.syfo.producer.arbeidsgivernotifikasjon.response.nyoppgave.NyOppgaveErrorResponse
import no.nav.syfo.producer.arbeidsgivernotifikasjon.response.nyoppgave.NyOppgaveResponse
import no.nav.syfo.producer.arbeidsgivernotifikasjon.response.nyoppgave.NyoppgaveMutationStatus.NY_OPPGAVE_VELLYKKET
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory

open class ArbeidsgiverNotifikasjonProdusent(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()
    private val arbeidsgiverNotifikasjonProdusentBasepath = urlEnv.arbeidsgiverNotifikasjonProdusentApiUrl
    private val log = LoggerFactory.getLogger(ArbeidsgiverNotifikasjonProdusent::class.qualifiedName)
    private val scope = urlEnv.arbeidsgiverNotifikasjonProdusentApiScope

    open suspend fun createNewNotificationForArbeidsgiver(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjon): String? {
        log.info("About to send new task with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api")
        val response: HttpResponse = callArbeidsgiverNotifikasjonProdusent(
            CREATE_NOTIFICATION_AG_MUTATION,
            arbeidsgiverNotifikasjon.createVariables(),
        )
        val nyBeskjed = response.body<NyBeskjedResponse>().data?.nyBeskjed
        val resultat = nyBeskjed?.__typename
        if (resultat == NY_BESKJED_VELLYKKET.status) {
            log.info("Have send new notification with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api")
            return nyBeskjed.id
        } else {
            if (resultat != null) {
                throw RuntimeException(nyBeskjed.feilmelding)
            }
            val errors = response.body<NyBeskjedErrorResponse>().errors
            throw RuntimeException("Could not send send notification to arbeidsgiver. because of error: ${errors[0].message}, data was null")
        }
    }

    open suspend fun createNewTaskForArbeidsgiver(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjon): String? {
        log.info("About to send new task with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api")
        val response: HttpResponse = callArbeidsgiverNotifikasjonProdusent(
            CREATE_TASK_AG_MUTATION,
            arbeidsgiverNotifikasjon.createVariables(),
        )
        val nyOppgave = response.body<NyOppgaveResponse>().data?.nyOppgave
        val resultat = nyOppgave?.__typename
        if (resultat == NY_OPPGAVE_VELLYKKET.status) {
            log.info("Have send new task with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api")
            return nyOppgave.id
        } else {
            if (resultat != null) {
                throw RuntimeException(nyOppgave.feilmelding)
            }
            val errors = response.body<NyOppgaveErrorResponse>().errors
            throw RuntimeException("Could not send task to arbeidsgiver. because of error: ${errors[0].message}, data was null")
        }
    }

    private fun ArbeidsgiverNotifikasjon.createVariables() = VariablesCreate(
        varselId,
        virksomhetsnummer,
        url,
        narmesteLederFnr,
        ansattFnr,
        merkelapp,
        messageText,
        narmesteLederEpostadresse,
        emailTitle,
        emailBody,
        EpostSendevinduTypes.LOEPENDE,
        hardDeleteDate.toString(),
    )

    open fun deleteNotifikasjonForArbeidsgiver(arbeidsgiverDeleteNotifikasjon: ArbeidsgiverDeleteNotifikasjon) {
        log.info("About to delete notification with uuid ${arbeidsgiverDeleteNotifikasjon.eksternReferanse} and merkelapp ${arbeidsgiverDeleteNotifikasjon.merkelapp} from ag-notifikasjon-produsent-api")
        callArbeidsgiverNotifikasjonProdusent(
            DELETE_NOTIFICATION_AG_MUTATION,
            VariablesDelete(
                arbeidsgiverDeleteNotifikasjon.merkelapp,
                arbeidsgiverDeleteNotifikasjon.eksternReferanse,
            ),
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
                val response = client.post(arbeidsgiverNotifikasjonProdusentBasepath) {
                    headers {
                        append(HttpHeaders.Accept, ContentType.Application.Json)
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                    if (requestBody != null) {
                        setBody(requestBody)
                    }
                }
                if (response.status != HttpStatusCode.OK) {
                    throw RuntimeException("Could not send to arbeidsgiver. Status code: ${response.status.value}. Response: $response")
                }
                response
            } catch (e: Exception) {
                log.error("Error while calling ag-notifikasjon-produsent-api ($mutationPath): ${e.message}", e)
                throw RuntimeException(
                    "Error while calling ag-notifikasjon-produsent-api ($mutationPath): ${e.message}",
                    e,
                )
            }
        }
    }
}
