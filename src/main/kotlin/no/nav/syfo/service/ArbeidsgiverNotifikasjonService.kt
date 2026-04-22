package no.nav.syfo.service

import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.producer.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProdusent
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverDeleteNotifikasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjonAltinnRessurs
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjonNarmesteLeder
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyKalenderInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyStatusSakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.OppdaterKalenderInput
import no.nav.syfo.service.Meldingstype.BESKJED
import no.nav.syfo.service.Meldingstype.OPPGAVE
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ArbeidsgiverNotifikasjonService(
    private val arbeidsgiverNotifikasjonProdusent: ArbeidsgiverNotifikasjonProdusent,
    private val narmesteLederService: NarmesteLederService,
    private val dineSykmeldteUrl: String,
) {
    private val log: Logger = LoggerFactory.getLogger(ArbeidsgiverNotifikasjonService::class.qualifiedName)

    suspend fun sendNotifikasjon(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjonInput) {
        val narmesteLederRelasjon =
            narmesteLederService.getNarmesteLederRelasjon(
                arbeidsgiverNotifikasjon.ansattFnr,
                arbeidsgiverNotifikasjon.virksomhetsnummer,
            )

        if (narmesteLederRelasjon == null || !narmesteLederService.hasNarmesteLederInfo(narmesteLederRelasjon)) {
            log.warn("Sender ikke varsel til ag-notifikasjon: narmesteLederRelasjon er null eller har ikke kontaktinfo")
            return
        }

        if (arbeidsgiverNotifikasjon.narmesteLederFnr !== null &&
            arbeidsgiverNotifikasjon.narmesteLederFnr != narmesteLederRelasjon.narmesteLederFnr
        ) {
            log.warn(
                "Sender ikke varsel til ag-notifikasjon: den ansatte har nærmeste leder med annet fnr enn mottaker i varselHendelse",
            )
            return
        }

        val url = arbeidsgiverNotifikasjon.link ?: "$dineSykmeldteUrl/${narmesteLederRelasjon.narmesteLederId}"

        val arbeidsgiverNotifikasjonNarmesteLeder =
            ArbeidsgiverNotifikasjonNarmesteLeder(
                varselId = arbeidsgiverNotifikasjon.uuid.toString(),
                virksomhetsnummer = arbeidsgiverNotifikasjon.virksomhetsnummer,
                url = url,
                narmesteLederFnr = narmesteLederRelasjon.narmesteLederFnr!!,
                ansattFnr = arbeidsgiverNotifikasjon.ansattFnr,
                messageText = arbeidsgiverNotifikasjon.messageText,
                narmesteLederEpostadresse = narmesteLederRelasjon.narmesteLederEpost!!,
                merkelapp = arbeidsgiverNotifikasjon.merkelapp,
                emailTitle = arbeidsgiverNotifikasjon.epostTittel,
                emailBody = arbeidsgiverNotifikasjon.epostHtmlBody,
                hardDeleteDate = arbeidsgiverNotifikasjon.hardDeleteDate,
                grupperingsid = arbeidsgiverNotifikasjon.grupperingsid,
            )

        when (arbeidsgiverNotifikasjon.meldingstype) {
            BESKJED ->
                arbeidsgiverNotifikasjonProdusent.createNewBeskjedForArbeidsgiver(
                    arbeidsgiverNotifikasjonNarmesteLeder,
                )

            OPPGAVE ->
                arbeidsgiverNotifikasjonProdusent.createNewOppgaveForArbeidsgiver(
                    arbeidsgiverNotifikasjonNarmesteLeder,
                )
        }
    }

    suspend fun sendNotifikasjon(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjonAltinnRessursInput) {
        val arbeidsgiverNotifikasjonAltinnRessurs =
            ArbeidsgiverNotifikasjonAltinnRessurs(
                varselId = arbeidsgiverNotifikasjon.uuid.toString(),
                virksomhetsnummer = arbeidsgiverNotifikasjon.virksomhetsnummer,
                url = arbeidsgiverNotifikasjon.ressursUrl,
                messageText = arbeidsgiverNotifikasjon.messageText,
                merkelapp = arbeidsgiverNotifikasjon.merkelapp,
                emailTitle = arbeidsgiverNotifikasjon.epostTittel,
                emailBody = arbeidsgiverNotifikasjon.epostHtmlBody,
                hardDeleteDate = arbeidsgiverNotifikasjon.hardDeleteDate,
                grupperingsid = arbeidsgiverNotifikasjon.grupperingsid,
                ressursId = arbeidsgiverNotifikasjon.ressursId,
            )
        when (arbeidsgiverNotifikasjon.meldingstype) {
            BESKJED ->
                arbeidsgiverNotifikasjonProdusent.createNewBeskjedForArbeidsgiver(
                    arbeidsgiverNotifikasjonAltinnRessurs,
                )

            OPPGAVE ->
                arbeidsgiverNotifikasjonProdusent.createNewOppgaveForArbeidsgiver(
                    arbeidsgiverNotifikasjonAltinnRessurs,
                )
        }
    }

    suspend fun deleteNotifikasjon(
        merkelapp: String,
        eksternReferanse: String,
    ) {
        arbeidsgiverNotifikasjonProdusent.deleteNotifikasjonForArbeidsgiver(
            ArbeidsgiverDeleteNotifikasjon(
                merkelapp,
                eksternReferanse,
            ),
        )
    }

    suspend fun createNewKalenderavtale(nyKalenderInput: NyKalenderInput): String? =
        arbeidsgiverNotifikasjonProdusent.createNewKalenderavtale(
            nyKalenderInput = nyKalenderInput,
        )

    suspend fun createNewSak(sakInput: NySakInput): String? = arbeidsgiverNotifikasjonProdusent.createNewSak(sakInput)

    suspend fun nyStatusSak(nyStatusSakInput: NyStatusSakInput): String? = arbeidsgiverNotifikasjonProdusent.nyStatusSak(nyStatusSakInput)

    suspend fun updateKalenderavtale(oppdaterKalenderInput: OppdaterKalenderInput): String? =
        arbeidsgiverNotifikasjonProdusent.updateKalenderavtale(oppdaterKalenderInput)
}

enum class Meldingstype {
    OPPGAVE,
    BESKJED,
}
