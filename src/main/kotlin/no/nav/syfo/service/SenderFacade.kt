package no.nav.syfo.service

import java.net.URL
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.Kanal.*
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.fetchUtsendtVarsel
import no.nav.syfo.db.setUtsendtVarselToFerdigstilt
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel

class SenderFacade(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val dittSykefravaerMeldingKafkaProducer: DittSykefravaerMeldingKafkaProducer,
    val brukernotifikasjonerService: BrukernotifikasjonerService,
    val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    val fysiskBrevUtsendingService: FysiskBrevUtsendingService,
    val database: DatabaseInterface
) {
    fun sendTilDineSykmeldte(
        varselHendelse: NarmesteLederHendelse,
        varsel: DineSykmeldteVarsel,
    ) {
        dineSykmeldteHendelseKafkaProducer.sendVarsel(varsel)
        lagreUtsendtNarmesteLederVarsel(DINE_SYKMELDTE, varselHendelse, varsel.id.toString())
    }

    fun sendTilDittSykefravaer(
        varselHendelse: ArbeidstakerHendelse,
        varsel: DittSykefravaerVarsel,
    ) {
        val eksternUUID = dittSykefravaerMeldingKafkaProducer.sendMelding(varsel.melding)
        lagreUtsendtArbeidstakerVarsel(DITT_SYKEFRAVAER, varselHendelse, eksternUUID)
    }

    fun sendTilBrukernotifikasjoner(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL,
        varselHendelse: ArbeidstakerHendelse,
        meldingType: BrukernotifikasjonKafkaProducer.MeldingType? = BrukernotifikasjonKafkaProducer.MeldingType.BESKJED,
    ) {
        brukernotifikasjonerService.sendVarsel(uuid, mottakerFnr, content, url, meldingType)
        lagreUtsendtArbeidstakerVarsel(BRUKERNOTIFIKASJON, varselHendelse, uuid)
    }

    fun ferdigstillBrukernotifkasjonVarsler(varselHendelse: ArbeidstakerHendelse) {
        val eksterneReferanser = database.fetchUtsendtVarsel(
            varselHendelse.arbeidstakerFnr,
            varselHendelse.orgnummer!!,
            varselHendelse.type,
            BRUKERNOTIFIKASJON
        ).eksterneRefUferdigstilteVarsler(varselHendelse.type)
        for (eksternReferanse in eksterneReferanser) {
            brukernotifikasjonerService.ferdigstillVarsel(eksternReferanse!!, varselHendelse.arbeidstakerFnr)
            database.setUtsendtVarselToFerdigstilt(eksternReferanse)
        }
    }

    fun sendTilArbeidsgiverNotifikasjon(
        varselHendelse: NarmesteLederHendelse,
        varsel: ArbeidsgiverNotifikasjonInput,
    ) {
        arbeidsgiverNotifikasjonService.sendNotifikasjon(varsel)
        lagreUtsendtNarmesteLederVarsel(ARBEIDSGIVERNOTIFIKASJON, varselHendelse, varsel.uuid.toString())
    }

    fun ferdigstillArbeidsgiverNotifikasjoner(
        varselHendelse: NarmesteLederHendelse,
        merkelapp: String
    ) {
        val eksterneReferanser = database.fetchUtsendtVarsel(
            varselHendelse.arbeidstakerFnr,
            varselHendelse.orgnummer,
            varselHendelse.type,
            ARBEIDSGIVERNOTIFIKASJON
        ).eksterneRefUferdigstilteVarsler(varselHendelse.type)
        for (eksternReferanse in eksterneReferanser) {
            arbeidsgiverNotifikasjonService.deleteNotifikasjon(
                merkelapp,
                eksternReferanse!!
            )
            database.setUtsendtVarselToFerdigstilt(eksternReferanse)
        }
    }

    fun ferdigstillDineSykmeldteVarsler(varselHendelse: NarmesteLederHendelse) {
        val eksterneReferanser = database.fetchUtsendtVarsel(
            varselHendelse.arbeidstakerFnr,
            varselHendelse.orgnummer,
            varselHendelse.type,
            DINE_SYKMELDTE
        ).eksterneRefUferdigstilteVarsler(varselHendelse.type)
        for (eksternReferanse in eksterneReferanser) {
            dineSykmeldteHendelseKafkaProducer.ferdigstillVarsel(eksternReferanse!!)
            database.setUtsendtVarselToFerdigstilt(eksternReferanse)
        }
    }

    fun sendBrevTilFysiskPrint(
        uuid: String,
        varselHendelse: ArbeidstakerHendelse,
        journalpostId: String,
    ) {
        fysiskBrevUtsendingService.sendBrev(uuid, journalpostId)
        lagreUtsendtArbeidstakerVarsel(BREV, varselHendelse, uuid)
    }

    fun lagreUtsendtNarmesteLederVarsel(
        kanal: Kanal,
        varselHendelse: NarmesteLederHendelse,
        eksternReferanse: String,
    ) {
        database.storeUtsendtVarsel(
            PUtsendtVarsel(
                UUID.randomUUID().toString(),
                varselHendelse.arbeidstakerFnr,
                null,
                varselHendelse.narmesteLederFnr,
                varselHendelse.orgnummer,
                varselHendelse.type.name,
                kanal.name,
                LocalDateTime.now(),
                null,
                eksternReferanse,
                null
            )
        )
    }

    fun lagreUtsendtArbeidstakerVarsel(
        kanal: Kanal,
        varselHendelse: ArbeidstakerHendelse,
        eksternReferanse: String,
    ) {
        database.storeUtsendtVarsel(
            PUtsendtVarsel(
                UUID.randomUUID().toString(),
                varselHendelse.arbeidstakerFnr,
                null,
                null,
                varselHendelse.orgnummer,
                varselHendelse.type.name,
                kanal.name,
                LocalDateTime.now(),
                null,
                eksternReferanse,
                null,
            )
        )
    }
}

fun List<PUtsendtVarsel>.eksterneRefUferdigstilteVarsler(hendelseType: HendelseType) =
    this
        .sortedByDescending { it.utsendtTidspunkt }
        .filter {
            it.eksternReferanse != null &&
                it.type == hendelseType.toString() &&
                it.ferdigstiltTidspunkt == null
        }
        .map { it.eksternReferanse }
