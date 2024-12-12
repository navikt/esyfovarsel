package no.nav.syfo.service

import com.apollo.graphql.type.KalenderavtaleTilstand
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.producer.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProdusent
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverDeleteNotifikasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjon
import no.nav.syfo.service.Meldingstype.BESKJED
import no.nav.syfo.service.Meldingstype.OPPGAVE
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

class ArbeidsgiverNotifikasjonService(
    private val arbeidsgiverNotifikasjonProdusent: ArbeidsgiverNotifikasjonProdusent,
    private val narmesteLederService: NarmesteLederService,
    private val dineSykmeldteUrl: String,
) {

    private val log: Logger = LoggerFactory.getLogger(ArbeidsgiverNotifikasjonService::class.qualifiedName)

    suspend fun sendNotifikasjon(
        arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjonInput,
    ) {
        val narmesteLederRelasjon = narmesteLederService.getNarmesteLederRelasjon(
            arbeidsgiverNotifikasjon.ansattFnr,
            arbeidsgiverNotifikasjon.virksomhetsnummer
        )

        if (narmesteLederRelasjon == null || !narmesteLederService.hasNarmesteLederInfo(narmesteLederRelasjon)) {
            log.warn("Sender ikke varsel til ag-notifikasjon: narmesteLederRelasjon er null eller har ikke kontaktinfo")
            return
        }

        if (arbeidsgiverNotifikasjon.narmesteLederFnr !== null && arbeidsgiverNotifikasjon.narmesteLederFnr != narmesteLederRelasjon.narmesteLederFnr) {
            log.warn("Sender ikke varsel til ag-notifikasjon: den ansatte har nærmeste leder med annet fnr enn mottaker i varselHendelse")
            return
        }

        val url = dineSykmeldteUrl + "/${narmesteLederRelasjon.narmesteLederId}"
        val arbeidsgiverNotifikasjonen = ArbeidsgiverNotifikasjon(
            arbeidsgiverNotifikasjon.uuid.toString(),
            arbeidsgiverNotifikasjon.virksomhetsnummer,
            url,
            narmesteLederRelasjon.narmesteLederFnr!!,
            arbeidsgiverNotifikasjon.ansattFnr,
            arbeidsgiverNotifikasjon.messageText,
            narmesteLederRelasjon.narmesteLederEpost!!,
            arbeidsgiverNotifikasjon.merkelapp,
            arbeidsgiverNotifikasjon.emailTitle,
            arbeidsgiverNotifikasjon.emailBody,
            arbeidsgiverNotifikasjon.hardDeleteDate,
        )

        when (arbeidsgiverNotifikasjon.meldingstype) {
            BESKJED -> arbeidsgiverNotifikasjonProdusent.createNewNotificationForArbeidsgiver(arbeidsgiverNotifikasjonen)
            OPPGAVE -> arbeidsgiverNotifikasjonProdusent.createNewTaskForArbeidsgiver(arbeidsgiverNotifikasjonen)
        }
    }


    suspend fun deleteNotifikasjon(merkelapp: String, eksternReferanse: String) {
        arbeidsgiverNotifikasjonProdusent.deleteNotifikasjonForArbeidsgiver(
            ArbeidsgiverDeleteNotifikasjon(
                merkelapp,
                eksternReferanse,
            ),
        )
    }

    suspend fun createNewKalenderavtale(
        nyKalenderInput: NyKalenderInput
    ): String? {
        val narmesteLederRelasjon = narmesteLederService.getNarmesteLederRelasjon(
            nyKalenderInput.sykmeldtFnr,
            nyKalenderInput.virksomhetsnummer
        )

        if (narmesteLederRelasjon?.narmesteLederId == null) {
            log.warn("Sender ikke kalenderavtale: narmesteLederRelasjon er null, eller mangler narmesteLederId")
            return null
        }

        val url = dineSykmeldteUrl + "/${narmesteLederRelasjon.narmesteLederId}"

        return arbeidsgiverNotifikasjonProdusent.createNewKalenderavtale(
            kalenderInput = nyKalenderInput,
            narmesteLederId = narmesteLederRelasjon.narmesteLederId,
            url = url
        )
    }

    suspend fun updateKalenderavtale(
        oppdaterKalenderInput: OppdaterKalenderInput
    ): String? {
        return arbeidsgiverNotifikasjonProdusent.updateKalenderavtale(oppdaterKalenderInput)
    }
}

data class ArbeidsgiverNotifikasjonInput(
    val uuid: UUID,
    val virksomhetsnummer: String,
    val narmesteLederFnr: String?,
    val ansattFnr: String,
    val merkelapp: String,
    val messageText: String,
    val emailTitle: String,
    val emailBody: String,
    val hardDeleteDate: LocalDateTime,
    val meldingstype: Meldingstype = BESKJED,
)

/**
 * @param virksomhetsnummer Organisasjonsnummeret til virksomheten som skal motta kalenderavtalen.
 * @param grupperingsid Grupperings-ID-en knytter denne kalenderavtalen til en sak med samme grupperings-ID og merkelapp. Det vises ikke til brukere. Saksnummer er en naturlig grupperings-ID.
 * @param merkelapp Merkelapp for kalenderavtalen. Er typisk navnet på ytelse eller lignende. Den vises ikke til brukeren, men brukes i kombinasjon med grupperings-ID for å koble kalenderavtalen til sak.
 * @param eksternId Den eksterne ID-en brukes for å unikt identifisere en notifikasjon. Den må være unik for merkelappen.
 * @param tekst Teksten som vises til brukeren.
 * @param sykmeldtFnr Fødselsnummeret til den sykmeldte
 * @param naermesteLederFnr Fødselsnummeret til nærmeste leder
 * @param startTidspunkt Når avtalen starter.
 * @param sluttTidspunkt Når avtalen slutter (valgfritt).
 * @param hardDeleteTidspunkt Når avtalen skal slettes.
 */
data class NyKalenderInput(
    val virksomhetsnummer: String,
    val grupperingsid: String,
    val merkelapp: String,
    val eksternId: String,
    val tekst: String,
    val sykmeldtFnr: String,
    val naermesteLederFnr: String,
    val startTidspunkt: LocalDateTime,
    val sluttTidspunkt: LocalDateTime?,
    val hardDeleteTidspunkt: LocalDateTime,
)

/**
 * @param id ID-en til kalenderavtalen som skal oppdateres.
 * @param nyTilstand Den nye tilstanden til avtalen.
 * @param nyTekst Den nye teksten som skal vises til brukeren.
 * @param nyLenke Den nye lenken som brukeren skal føres til.
 * @param hardDeleteTidspunkt Når avtalen skal slettes.
 */
data class OppdaterKalenderInput(
    val id: String,
    val nyTilstand: KalenderavtaleTilstand,
    val nyTekst: String,
    val nyLenke: String?,
    val hardDeleteTidspunkt: LocalDateTime,
)

enum class Meldingstype {
    OPPGAVE,
    BESKJED,
}
