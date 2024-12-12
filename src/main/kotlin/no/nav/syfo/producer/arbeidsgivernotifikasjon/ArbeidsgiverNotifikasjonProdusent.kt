package no.nav.syfo.producer.arbeidsgivernotifikasjon

import com.apollo.graphql.NyBeskjedMutation
import com.apollo.graphql.NyKalenderavtaleMutation
import com.apollo.graphql.NySakMutation
import com.apollo.graphql.NyStatusSakByGrupperingsidMutation
import com.apollo.graphql.OppdaterKalenderavtaleMutation
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import no.nav.syfo.UrlEnv
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverDeleteNotifikasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyKalenderInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyStatusSakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.OppdaterKalenderInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.toNyBeskjedMutation
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.toNyKalenderavtaleMutation
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.toNySakMutation
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.toNyStatusSakByGrupperingsidMutation
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.toOppdaterKalenderavtaleMutation
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
    private val apolloClient = ApolloClient.Builder()
        .serverUrl(arbeidsgiverNotifikasjonProdusentBasepath)
        .addInterceptor(BearerTokenInterceptor { azureAdTokenConsumer.getToken(scope) })
        .build()

    suspend fun createNewOppgaveForArbeidsgiver(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjon): String? {
        log.info(
            "About to send new task with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api"
        )
        val response: HttpResponse = callArbeidsgiverNotifikasjonProdusent(
            CREATE_TASK_AG_MUTATION,
            arbeidsgiverNotifikasjon.createVariables(),
        )
        val nyOppgave = response.body<NyOppgaveResponse>().data?.nyOppgave
        val resultat = nyOppgave?.__typename
        if (resultat == NY_OPPGAVE_VELLYKKET.status) {
            log.info(
                "Have send new task with uuid ${arbeidsgiverNotifikasjon.varselId} to ag-notifikasjon-produsent-api"
            )
            return nyOppgave.id
        } else {
            if (resultat != null) {
                throw RuntimeException(nyOppgave.feilmelding)
            }
            val errors = response.body<NyOppgaveErrorResponse>().errors
            throw RuntimeException(
                "Could not send task to arbeidsgiver. because of error: ${errors[0].message}, data was null"
            )
        }
    }

    suspend fun createNewBeskjedForArbeidsgiver(arbeidsgiverNotifikasjonInput: ArbeidsgiverNotifikasjon): String? {
        log.info("About to send new beskjed to ag-notifikasjon-produsent-api")
        val response: ApolloResponse<NyBeskjedMutation.Data> =
            apolloClient.mutation(arbeidsgiverNotifikasjonInput.toNyBeskjedMutation()).execute()
        val result = response.data?.nyBeskjed

        if (result?.onNyBeskjedVellykket != null) {
            log.info("Created new beskjed with id ${result.onNyBeskjedVellykket.id}")
            return result.onNyBeskjedVellykket.id
        } else {
            log.error("Could not create new beskjed")
            response.errors?.forEach {
                log.error("createNewBeskjed response error: ${it.message}")
            }
            result?.onUgyldigMerkelapp?.let {
                log.error("createNewBeskjed - Ugyldig merkelapp: ${it.feilmelding}")
            }
            result?.onUgyldigMottaker?.let {
                log.error("createNewBeskjed - Ugyldig mottaker: ${it.feilmelding}")
            }
            result?.onUkjentProdusent?.let {
                log.error("createNewBeskjed - Ukjent produsent: ${it.feilmelding}")
            }
            result?.onUkjentRolle?.let {
                log.error("createNewBeskjed - Ukjent rolle: ${it.feilmelding}")
            }
            result?.onDuplikatEksternIdOgMerkelapp?.let {
                log.error(
                    "createNewBeskjed - Duplikat ekstern id og merkelapp: ${it.feilmelding}, Existing ID: ${it.idTilEksisterende}"
                )
                return it.idTilEksisterende
            }
            return null
        }
    }

    suspend fun createNewSak(
        nySakInput: NySakInput
    ): String? {
        log.info("About to create new sak in ag-notifikasjon-produsent-api")
        val response: ApolloResponse<NySakMutation.Data> = apolloClient.mutation(nySakInput.toNySakMutation()).execute()
        val result = response.data?.nySak

        if (result?.onNySakVellykket != null) {
            log.info("Created new sak with id ${result.onNySakVellykket.id}")
            return result.onNySakVellykket.id
        } else {
            log.error("Could not create new sak")
            response.errors?.forEach {
                log.error("Response errors when creating new sak: ${it.message}")
            }
            result?.onUgyldigMerkelapp?.let {
                log.error("createNewSak - Ugyldig merkelapp: ${it.feilmelding}")
            }
            result?.onUgyldigMottaker?.let {
                log.error("createNewSak - Ugyldig mottaker: ${it.feilmelding}")
            }
            result?.onDuplikatGrupperingsid?.let {
                log.error("createNewSak - Duplikat grupperingsid: ${it.feilmelding}")
                return it.idTilEksisterende
            }
            result?.onDuplikatGrupperingsidEtterDelete?.let {
                log.error("createNewSak - Duplikat grupperingsid etter delete: ${it.feilmelding}")
            }
            result?.onUkjentProdusent?.let {
                log.error("createNewSak - Ukjent produsent: ${it.feilmelding}")
            }
            result?.onUkjentRolle?.let {
                log.error("createNewSak - Ukjent rolle: ${it.feilmelding}")
            }
            return null
        }
    }

    suspend fun nyStatusSak(
        nyStatusSakInput: NyStatusSakInput
    ): String? {
        log.info("About to update sakstatus in ag-notifikasjon-produsent-api")
        val response: ApolloResponse<NyStatusSakByGrupperingsidMutation.Data> =
            apolloClient.mutation(nyStatusSakInput.toNyStatusSakByGrupperingsidMutation()).execute()
        val result = response.data?.nyStatusSakByGrupperingsid

        if (result?.onNyStatusSakVellykket != null) {
            log.info("Updated sak with id ${result.onNyStatusSakVellykket.id}")
            return result.onNyStatusSakVellykket.id
        } else {
            log.error("Could not update sak")
            response.errors?.forEach {
                log.error("Response errors when updating sak: ${it.message}")
            }
            result?.onUgyldigMerkelapp?.let {
                log.error("updateSak - Ugyldig merkelapp: ${it.feilmelding}")
            }
            result?.onUkjentProdusent?.let {
                log.error("updateSak - Ukjent produsent: ${it.feilmelding}")
            }
            result?.onSakFinnesIkke?.let {
                log.error("updateSak - Sak finnes ikke: ${it.feilmelding}")
            }
            result?.onKonflikt?.let {
                log.error("updateSak - Konflikt: ${it.feilmelding}")
            }
            return null
        }
    }

    suspend fun createNewKalenderavtale(
        nyKalenderInput: NyKalenderInput,
    ): String? {
        log.info("Forsøker å opprette ny kalenderavtale")

        val response: ApolloResponse<NyKalenderavtaleMutation.Data> =
            apolloClient.mutation(nyKalenderInput.toNyKalenderavtaleMutation()).execute()
        val result = response.data?.nyKalenderavtale

        if (result?.onNyKalenderavtaleVellykket != null) {
            log.info("Opprettet ny kalenderavtale!")
            return result.onNyKalenderavtaleVellykket.id
        } else {
            log.error("Feil ved oppretting av kalenderavtale")

            response.errors?.forEach {
                log.error("Response errors ved oppretting av kalenderavtale: ${it.message}")
            }

            result?.onUgyldigKalenderavtale?.let {
                log.error("createNewKalenderavtale - Ugyldig kalenderavtale: ${it.feilmelding}")
            }
            result?.onUgyldigMerkelapp?.let {
                log.error("createNewKalenderavtale - Ugyldig merkelapp: ${it.feilmelding}")
            }
            result?.onUgyldigMottaker?.let {
                log.error("createNewKalenderavtale - Ugyldig mottaker: ${it.feilmelding}")
            }
            result?.onDuplikatEksternIdOgMerkelapp?.let {
                log.error(
                    "createNewKalenderavtale - Duplikat ekstern id og merkelapp: ${it.feilmelding}, Existing ID: ${it.idTilEksisterende}"
                )
                return it.idTilEksisterende
            }
            result?.onUkjentProdusent?.let {
                log.error("createNewKalenderavtale - Ukjent produsent: ${it.feilmelding}")
            }
            result?.onSakFinnesIkke?.let {
                log.error("createNewKalenderavtale - Sak finnes ikke: ${it.feilmelding}")
            }
            return null
        }
    }

    suspend fun updateKalenderavtale(
        oppdaterKalenderInput: OppdaterKalenderInput
    ): String? {
        val response: ApolloResponse<OppdaterKalenderavtaleMutation.Data> =
            apolloClient.mutation(oppdaterKalenderInput.toOppdaterKalenderavtaleMutation()).execute()
        val result = response.data?.oppdaterKalenderavtale

        if (result?.onOppdaterKalenderavtaleVellykket != null) {
            log.info("Oppdaterte kalenderavtale!")
            return result.onOppdaterKalenderavtaleVellykket.id
        } else {
            log.error("Feil ved oppdatering av kalenderavtale")

            response.errors?.forEach {
                log.error("Response errors ved oppdatering av kalenderavtale: ${it.message}")
            }

            result?.onUgyldigKalenderavtale?.let {
                log.error("updateKalenderavtale - Ugyldig kalenderavtale: ${it.feilmelding}")
            }
            result?.onUgyldigMerkelapp?.let {
                log.error("updateKalenderavtale - Ugyldig merkelapp: ${it.feilmelding}")
            }
            result?.onUkjentProdusent?.let {
                log.error("updateKalenderavtale - Ukjent produsent: ${it.feilmelding}")
            }
            result?.onNotifikasjonFinnesIkke?.let {
                log.error("updateKalenderavtale - Notifikasjon finnes ikke: ${it.feilmelding}")
            }
            result?.onKonflikt?.let {
                log.error("updateKalenderavtale - Konflikt: ${it.feilmelding}")
            }
            return null
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
        log.info(
            "About to delete notification with uuid ${arbeidsgiverDeleteNotifikasjon.eksternReferanse} and merkelapp ${arbeidsgiverDeleteNotifikasjon.merkelapp} from ag-notifikasjon-produsent-api"
        )
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
                throw RuntimeException(
                    "Could not send to arbeidsgiver. Status code: ${response.status.value}. Response: $response"
                )
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
