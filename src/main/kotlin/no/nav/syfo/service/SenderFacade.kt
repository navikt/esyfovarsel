package no.nav.syfo.service

import java.net.URL
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.db.*
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.Kanal.*
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel

class SenderFacade(
    private val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    private val dittSykefravaerMeldingKafkaProducer: DittSykefravaerMeldingKafkaProducer,
    private val brukernotifikasjonerService: BrukernotifikasjonerService,
    private val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    private val fysiskBrevUtsendingService: FysiskBrevUtsendingService,
    val database: DatabaseInterface,
) {
    fun sendTilDineSykmeldte(
        varselHendelse: NarmesteLederHendelse,
        varsel: DineSykmeldteVarsel,
    ) {
        var isSendingSucceed = true
        try {
            dineSykmeldteHendelseKafkaProducer.sendVarsel(varsel)
        } catch (e: Exception) {
            isSendingSucceed = false
            log.warn("Error while sending varsel to DINE_SYKMELDTE: ${e.message}", e)
            lagreIkkeUtsendtNarmesteLederVarsel(
                kanal = DINE_SYKMELDTE,
                varselHendelse = varselHendelse,
                eksternReferanse = varsel.id.toString(),
                feilmelding = e.message,
            )
        }
        if (isSendingSucceed) {
            lagreUtsendtNarmesteLederVarsel(DINE_SYKMELDTE, varselHendelse, varsel.id.toString())
        }
    }

    fun sendTilDittSykefravaer(
        varselHendelse: ArbeidstakerHendelse,
        varsel: DittSykefravaerVarsel,
    ) {
        var eksternUUID = ""
        var isSendingSucceed = true
        try {
            eksternUUID = dittSykefravaerMeldingKafkaProducer.sendMelding(varsel.melding)
        } catch (e: Exception) {
            log.warn("Error while sending varsel to DITT_SYKEFRAVAER: ${e.message}")
            isSendingSucceed = false
            lagreIkkeUtsendtArbeidstakerVarsel(
                kanal = DITT_SYKEFRAVAER,
                varselHendelse = varselHendelse,
                eksternReferanse = varsel.uuid,
                feilmelding = e.message,
                journalpostId = null,
            )
        }
        if (isSendingSucceed && eksternUUID.isNotBlank()) {
            lagreUtsendtArbeidstakerVarsel(DITT_SYKEFRAVAER, varselHendelse, eksternUUID)
        }
    }

    fun sendTilBrukernotifikasjoner(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL,
        varselHendelse: ArbeidstakerHendelse,
        meldingType: BrukernotifikasjonKafkaProducer.MeldingType? = BrukernotifikasjonKafkaProducer.MeldingType.BESKJED,
    ) {
        var isSendingSucceed = true
        try {
            brukernotifikasjonerService.sendVarsel(uuid, mottakerFnr, content, url, meldingType)
        } catch (e: Exception) {
            log.warn("Error while sending varsel to BRUKERNOTIFIKASJON: ${e.message}")
            isSendingSucceed = false
            lagreIkkeUtsendtArbeidstakerVarsel(
                kanal = BRUKERNOTIFIKASJON,
                varselHendelse = varselHendelse,
                eksternReferanse = uuid,
                feilmelding = e.message,
                journalpostId = null,
            )
        }
        if (isSendingSucceed) {
            lagreUtsendtArbeidstakerVarsel(BRUKERNOTIFIKASJON, varselHendelse, uuid)
        }
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
        var isSendingSucceed = true
        try {
            arbeidsgiverNotifikasjonService.sendNotifikasjon(varsel)
        } catch (e: Exception) {
            log.warn("Error while sending varsel to ARBEIDSGIVERNOTIFIKASJON: ${e.message}")
            isSendingSucceed = false
            lagreIkkeUtsendtNarmesteLederVarsel(
                kanal = ARBEIDSGIVERNOTIFIKASJON,
                varselHendelse = varselHendelse,
                eksternReferanse = varsel.uuid.toString(),
                feilmelding = e.message,
            )
        }
        if (isSendingSucceed) {
            lagreUtsendtNarmesteLederVarsel(ARBEIDSGIVERNOTIFIKASJON, varselHendelse, varsel.uuid.toString())
        }
    }

    fun ferdigstillArbeidsgiverNotifikasjoner(
        varselHendelse: NarmesteLederHendelse,
        merkelapp: String,
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
        var isSendingSucceed = true
        try {
            fysiskBrevUtsendingService.sendBrev(uuid, journalpostId)
        } catch (e: Exception) {
            isSendingSucceed = false
            log.warn("Error while sending brev til fysisk print: ${e.message}")
            lagreIkkeUtsendtArbeidstakerVarsel(
                kanal = BREV,
                varselHendelse = varselHendelse,
                eksternReferanse = uuid,
                feilmelding = e.message,
                journalpostId = journalpostId,
            )
        }
        if (isSendingSucceed) {
            lagreUtsendtArbeidstakerVarsel(BREV, varselHendelse, uuid)
        }
    }

    private fun lagreUtsendtNarmesteLederVarsel(
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

    private fun lagreUtsendtArbeidstakerVarsel(
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

    private fun lagreIkkeUtsendtArbeidstakerVarsel(
        kanal: Kanal,
        varselHendelse: ArbeidstakerHendelse,
        eksternReferanse: String,
        feilmelding: String?,
        journalpostId: String? = null,
    ) {
        database.storeUtsendtVarselFeilet(
            PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                fnr = varselHendelse.arbeidstakerFnr,
                narmesteLederFnr = null,
                orgnummer = varselHendelse.orgnummer,
                type = varselHendelse.type.name,
                kanal = kanal.name,
                utsendtForsokTidspunkt = LocalDateTime.now(),
                feilmelding = feilmelding,
                journalpostId = journalpostId,
                eksternReferanse = eksternReferanse,
            )
        )
    }

    private fun lagreIkkeUtsendtNarmesteLederVarsel(
        kanal: Kanal,
        varselHendelse: NarmesteLederHendelse,
        eksternReferanse: String,
        feilmelding: String?,
    ) {
        database.storeUtsendtVarselFeilet(
            PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                fnr = varselHendelse.arbeidstakerFnr,
                narmesteLederFnr = varselHendelse.narmesteLederFnr,
                orgnummer = varselHendelse.orgnummer,
                type = varselHendelse.type.name,
                kanal = kanal.name,
                utsendtForsokTidspunkt = LocalDateTime.now(),
                feilmelding = feilmelding,
                journalpostId = null,
                eksternReferanse = eksternReferanse,
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
