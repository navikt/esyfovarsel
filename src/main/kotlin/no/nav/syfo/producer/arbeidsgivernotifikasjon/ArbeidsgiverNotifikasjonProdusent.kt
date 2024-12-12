package no.nav.syfo.producer.arbeidsgivernotifikasjon

import com.apollo.graphql.NyKalenderavtaleMutation
import com.apollo.graphql.OppdaterKalenderavtaleMutation
import com.apollo.graphql.type.FutureTemporalInput
import com.apollo.graphql.type.HardDeleteUpdateInput
import com.apollo.graphql.type.KalenderavtaleTilstand
import com.apollo.graphql.type.MottakerInput
import com.apollo.graphql.type.NaermesteLederMottakerInput
import com.apollo.graphql.type.NyTidStrategi
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
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
import no.nav.syfo.service.NyKalenderInput
import no.nav.syfo.service.OppdaterKalenderInput
import no.nav.syfo.utils.httpClient
import org.slf4j.LoggerFactory
import java.time.format.DateTimeFormatter

open class ArbeidsgiverNotifikasjonProdusent(urlEnv: UrlEnv, private val azureAdTokenConsumer: AzureAdTokenConsumer) {
    private val client = httpClient()
    private val arbeidsgiverNotifikasjonProdusentBasepath = urlEnv.arbeidsgiverNotifikasjonProdusentApiUrl
    private val log = LoggerFactory.getLogger(ArbeidsgiverNotifikasjonProdusent::class.qualifiedName)
    private val scope = urlEnv.arbeidsgiverNotifikasjonProdusentApiScope

    suspend fun createNewNotificationForArbeidsgiver(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjon): String? {
        log.info("About to send new notification with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api")
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

    suspend fun createNewTaskForArbeidsgiver(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjon): String? {
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

    suspend fun createNewKalenderavtale(
        kalenderInput: NyKalenderInput,
        narmesteLederId: String,
        url: String
    ): String? {
        log.info("Forsøker å opprette ny kalenderavtale")
        val apolloClient = ApolloClient.Builder()
            .serverUrl(arbeidsgiverNotifikasjonProdusentBasepath)
            .addInterceptor(BearerTokenInterceptor { azureAdTokenConsumer.getToken(scope) })
            .build()

        val mutation = NyKalenderavtaleMutation(
            virksomhetsnummer = kalenderInput.virksomhetsnummer,
            grupperingsid = narmesteLederId,
            merkelapp = kalenderInput.merkelapp,
            eksternId = kalenderInput.eksternId,
            tekst = kalenderInput.tekst,
            lenke = url,
            mottakere = listOf(
                MottakerInput(
                    naermesteLeder = Optional.present(
                        NaermesteLederMottakerInput(
                            naermesteLederFnr = kalenderInput.naermesteLederFnr,
                            ansattFnr = kalenderInput.sykmeldtFnr
                        )
                    )
                )
            ),
            startTidspunkt = kalenderInput.startTidspunkt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            sluttTidspunkt = Optional.presentIfNotNull(kalenderInput.sluttTidspunkt?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
            lokasjon = Optional.absent(),
            erDigitalt = Optional.absent(),
            tilstand = Optional.present(KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER),
            eksterneVarsler = listOf(),
            paaminnelse = Optional.absent(),
            hardDelete = Optional.present(FutureTemporalInput(den = Optional.present(kalenderInput.hardDeleteTidspunkt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))),
        )

        val response: ApolloResponse<NyKalenderavtaleMutation.Data> = apolloClient.mutation(mutation).execute()

        response.errors?.forEach {
            log.error("Error while creating new kalenderavtale: ${it.message}")
        }

        val result = response.data?.nyKalenderavtale

        return when {
            result?.onNyKalenderavtaleVellykket != null -> {
                log.info("Opprettet ny kalenderavtale!")
                result.onNyKalenderavtaleVellykket.id
            }

            result?.onUgyldigKalenderavtale != null -> {
                log.error("Feil ved oppretting av kalenderavtale - Ugyldig kalenderavtale: ${result.onUgyldigKalenderavtale.feilmelding}")
                null
            }

            result?.onUgyldigMerkelapp != null -> {
                log.error("Feil ved oppretting av kalenderavtale - Ugyldig merkelapp: ${result.onUgyldigMerkelapp.feilmelding}")
                null
            }

            result?.onUgyldigMottaker != null -> {
                log.error("Feil ved oppretting av kalenderavtale - Ugyldig mottaker: ${result.onUgyldigMottaker.feilmelding}")
                null
            }

            result?.onDuplikatEksternIdOgMerkelapp != null -> {
                log.error("Feil ved oppretting av kalenderavtale - Duplikat ekstern id og merkelapp: ${result.onDuplikatEksternIdOgMerkelapp.feilmelding}, Existing ID: ${result.onDuplikatEksternIdOgMerkelapp.idTilEksisterende}")
                null
            }

            result?.onUkjentProdusent != null -> {
                log.error("Feil ved oppretting av kalenderavtale - Ukjent produsent: ${result.onUkjentProdusent.feilmelding}")
                null
            }

            result?.onSakFinnesIkke != null -> {
                log.error("Feil ved oppretting av kalenderavtale - Sak finnes ikke: ${result.onSakFinnesIkke.feilmelding}")
                null
            }

            else -> {
                log.error("Feil ved oppretting av kalenderavtale - Uventet feil")
                null
            }
        }
    }

    suspend fun updateKalenderavtale(
        oppdaterKalenderInput: OppdaterKalenderInput
    ): String? {
        val apolloClient = ApolloClient.Builder()
            .serverUrl(arbeidsgiverNotifikasjonProdusentBasepath)
            .build()

        val mutation = OppdaterKalenderavtaleMutation(
            id = oppdaterKalenderInput.id,
            eksterneVarsler = listOf(),
            paaminnelse = Optional.absent(),
            hardDelete = Optional.present(HardDeleteUpdateInput(
                nyTid = FutureTemporalInput(
                    den = Optional.present(oppdaterKalenderInput.hardDeleteTidspunkt),
                ),
                strategi = NyTidStrategi.OVERSKRIV,
            )),
        )

        val response: ApolloResponse<OppdaterKalenderavtaleMutation.Data> = apolloClient.mutation(mutation).execute()
        val result = response.data?.oppdaterKalenderavtale

        return when {
            result?.onOppdaterKalenderavtaleVellykket != null -> {
                log.info("Opprettet ny kalenderavtale!")
                result.onOppdaterKalenderavtaleVellykket.id
            }

            result?.onUgyldigKalenderavtale != null -> {
                log.error("Feil ved oppdatering av kalenderavtale - Ugyldig kalenderavtale: ${result.onUgyldigKalenderavtale.feilmelding}")
                null
            }

            result?.onUgyldigMerkelapp != null -> {
                log.error("Feil ved oppdatering av kalenderavtale - Ugyldig merkelapp: ${result.onUgyldigMerkelapp.feilmelding}")
                null
            }

            result?.onUkjentProdusent != null -> {
                log.error("Feil ved oppdatering av kalenderavtale - Ukjent produsent: ${result.onUkjentProdusent.feilmelding}")
                null
            }

            result?.onNotifikasjonFinnesIkke != null -> {
                log.error("Feil ved oppdatering av kalenderavtale - Notifikasjon finnes ikke: ${result.onNotifikasjonFinnesIkke.feilmelding}")
                null
            }

            result?.onKonflikt != null -> {
                log.error("Feil ved oppdatering av kalenderavtale - Konflikt: ${result.onKonflikt.feilmelding}")
                null
            }

            else -> {
                log.error("Feil ved oppdatering av kalenderavtale - Uventet feil")
                null
            }
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

    suspend fun deleteNotifikasjonForArbeidsgiver(arbeidsgiverDeleteNotifikasjon: ArbeidsgiverDeleteNotifikasjon) {
        log.info("About to delete notification with uuid ${arbeidsgiverDeleteNotifikasjon.eksternReferanse} and merkelapp ${arbeidsgiverDeleteNotifikasjon.merkelapp} from ag-notifikasjon-produsent-api")
        callArbeidsgiverNotifikasjonProdusent(
            DELETE_NOTIFICATION_AG_MUTATION,
            VariablesDelete(
                arbeidsgiverDeleteNotifikasjon.merkelapp,
                arbeidsgiverDeleteNotifikasjon.eksternReferanse,
            ),
        )
    }

    private suspend fun callArbeidsgiverNotifikasjonProdusent(
        mutationPath: String,
        variables: Variables,
    ): HttpResponse {
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
            return response
        } catch (e: Exception) {
            log.error("Error while calling ag-notifikasjon-produsent-api ($mutationPath): ${e.message}", e)
            throw RuntimeException(
                "Error while calling ag-notifikasjon-produsent-api ($mutationPath): ${e.message}",
                e,
            )
        }
    }
}

class BearerTokenInterceptor(private val tokenProvider: suspend () -> String) : ApolloInterceptor {
    override fun <D : Operation.Data> intercept(
        request: ApolloRequest<D>,
        chain: ApolloInterceptorChain
    ): Flow<ApolloResponse<D>> = flow {
        val token = withContext(Dispatchers.IO) { tokenProvider() }
        val newRequest = request.newBuilder()
            .addHttpHeader("Authorization", "Bearer $token")
            .addHttpHeader("Content-Type", "application/json")
            .addHttpHeader("Accept", "application/json")
            .build()
        emitAll(chain.proceed(newRequest))
    }
}