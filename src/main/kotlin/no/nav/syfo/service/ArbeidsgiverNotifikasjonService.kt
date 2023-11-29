package no.nav.syfo.service

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

        if (arbeidsgiverNotifikasjon.narmesteLederFnr !== null && !arbeidsgiverNotifikasjon.narmesteLederFnr.equals(
                narmesteLederRelasjon.narmesteLederFnr
            )
        ) {
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

enum class Meldingstype {
    OPPGAVE,
    BESKJED,
}
