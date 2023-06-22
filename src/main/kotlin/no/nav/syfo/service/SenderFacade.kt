package no.nav.syfo.service

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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.LocalDateTime
import java.util.*

class SenderFacade(
    private val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    private val dittSykefravaerMeldingKafkaProducer: DittSykefravaerMeldingKafkaProducer,
    private val brukernotifikasjonerService: BrukernotifikasjonerService,
    private val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    private val fysiskBrevUtsendingService: FysiskBrevUtsendingService,
    val database: DatabaseInterface,
) {
    private val log: Logger = LoggerFactory.getLogger(SenderFacade::class.qualifiedName)
    fun sendTilDineSykmeldte(
        varselHendelse: NarmesteLederHendelse,
        varsel: DineSykmeldteVarsel,
    ) {
        try {
            dineSykmeldteHendelseKafkaProducer.sendVarsel(varsel)
            lagreUtsendtNarmesteLederVarsel(DINE_SYKMELDTE, varselHendelse, varsel.id.toString())
        } catch (e: Exception) {
            log.warn("Error while sending varsel to DINE_SYKMELDTE: ${e.message}", e)
            lagreIkkeUtsendtNarmesteLederVarsel(
                kanal = DINE_SYKMELDTE,
                varselHendelse = varselHendelse,
                eksternReferanse = varsel.id.toString(),
                feilmelding = e.message,
                merkelapp = null,
            )
        }
    }

    fun sendTilDittSykefravaer(
        varselHendelse: ArbeidstakerHendelse,
        varsel: DittSykefravaerVarsel,
    ) {
        try {
            val eksternUUID = dittSykefravaerMeldingKafkaProducer.sendMelding(varsel.melding)
            lagreUtsendtArbeidstakerVarsel(DITT_SYKEFRAVAER, varselHendelse, eksternUUID)
        } catch (e: Exception) {
            log.warn("Error while sending varsel to DITT_SYKEFRAVAER: ${e.message}")
            lagreIkkeUtsendtArbeidstakerVarsel(
                kanal = DITT_SYKEFRAVAER,
                varselHendelse = varselHendelse,
                eksternReferanse = varsel.uuid,
                feilmelding = e.message,
                journalpostId = null,
                brukernotifikasjonerMeldingType = null,
            )
        }
    }

    fun sendTilBrukernotifikasjoner(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL,
        varselHendelse: ArbeidstakerHendelse,
        meldingType: BrukernotifikasjonKafkaProducer.MeldingType? = BrukernotifikasjonKafkaProducer.MeldingType.BESKJED,
        eksternVarsling: Boolean = true
    ) {
        var isSendingSucceed = true
        try {
            brukernotifikasjonerService.sendVarsel(uuid, mottakerFnr, content, url, meldingType, eksternVarsling)
        } catch (e: Exception) {
            log.warn("Error while sending varsel to BRUKERNOTIFIKASJON: ${e.message}")
            isSendingSucceed = false
            lagreIkkeUtsendtArbeidstakerVarsel(
                kanal = BRUKERNOTIFIKASJON,
                varselHendelse = varselHendelse,
                eksternReferanse = uuid,
                feilmelding = e.message,
                journalpostId = null,
                brukernotifikasjonerMeldingType = meldingType?.name
                    ?: BrukernotifikasjonKafkaProducer.MeldingType.BESKJED.name,
            )
        }
        if (isSendingSucceed) {
            lagreUtsendtArbeidstakerVarsel(BRUKERNOTIFIKASJON, varselHendelse, uuid)
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
                merkelapp = varsel.merkelapp,
            )
        }
        if (isSendingSucceed) {
            lagreUtsendtNarmesteLederVarsel(ARBEIDSGIVERNOTIFIKASJON, varselHendelse, varsel.uuid.toString(), varsel.merkelapp)
        }
    }

    fun ferdigstillArbeidsgiverNotifikasjoner(
        varselHendelse: NarmesteLederHendelse,
    ) {
        val uferdigstilteVarsler = database.fetchUtsendtVarsel(
            varselHendelse.arbeidstakerFnr,
            varselHendelse.orgnummer,
            varselHendelse.type,
            ARBEIDSGIVERNOTIFIKASJON,
        ).uferdigstilteVarsler(varselHendelse.type)
        for (varsel in uferdigstilteVarsler) {
            if (varsel.eksternReferanse != null && varsel.arbeidsgivernotifikasjonMerkelapp != null) {
                arbeidsgiverNotifikasjonService.deleteNotifikasjon(
                    varsel.arbeidsgivernotifikasjonMerkelapp,
                    varsel.eksternReferanse,
                )
                database.setUtsendtVarselToFerdigstilt(varsel.eksternReferanse)
            } else {
                log.warn("Kan ikke ferdigstille varsel med uuid ${varsel.uuid} fordi den mangler eksternReferanse eller arbeidsgivernotifikasjonMerkelapp")
            }
        }
    }

    fun ferdigstillDineSykmeldteVarsler(varselHendelse: NarmesteLederHendelse) {
        val uferdigstilteVarsler = database.fetchUtsendtVarsel(
            varselHendelse.arbeidstakerFnr,
            varselHendelse.orgnummer,
            varselHendelse.type,
            DINE_SYKMELDTE,
        ).uferdigstilteVarsler(varselHendelse.type)
        for (varsel in uferdigstilteVarsler) {
            dineSykmeldteHendelseKafkaProducer.ferdigstillVarsel(varsel.eksternReferanse!!)
            database.setUtsendtVarselToFerdigstilt(varsel.eksternReferanse)
        }
    }

    fun ferdigstillArbeidstakerVarsler(varselHendelse: ArbeidstakerHendelse) {
        ferdigstillBrukernotifkasjonVarsler(varselHendelse)
        ferdigstillDittSykefravaerVarsler(varselHendelse)
    }

    private fun ferdigstillBrukernotifkasjonVarsler(varselHendelse: ArbeidstakerHendelse) {
        for (varsel in fetchUferdigstilteVarsler(varselHendelse, BRUKERNOTIFIKASJON)) {
            varsel.eksternReferanse?.let {
                brukernotifikasjonerService.ferdigstillVarsel(it, varselHendelse.arbeidstakerFnr)
                database.setUtsendtVarselToFerdigstilt(it)
            }
        }
    }

    private fun ferdigstillDittSykefravaerVarsler(varselHendelse: ArbeidstakerHendelse) {
        for (varsel in fetchUferdigstilteVarsler(varselHendelse, DITT_SYKEFRAVAER)) {
            varsel.eksternReferanse?.let {
                dittSykefravaerMeldingKafkaProducer.ferdigstillMelding(it, varselHendelse.arbeidstakerFnr)
                database.setUtsendtVarselToFerdigstilt(it)
            }
        }
    }

    private fun fetchUferdigstilteVarsler(varselHendelse: ArbeidstakerHendelse, kanal: Kanal): List<PUtsendtVarsel> {
        val fetchUtsendtVarsel = if (varselHendelse.orgnummer != null) {
            database.fetchUtsendtVarsel(
                varselHendelse.arbeidstakerFnr,
                varselHendelse.orgnummer,
                varselHendelse.type,
                kanal,
            )
        } else {
            database.fetchUtsendtVarsel(
                varselHendelse.arbeidstakerFnr,
                varselHendelse.type,
                kanal,
            )
        }

        return fetchUtsendtVarsel.uferdigstilteVarsler(varselHendelse.type)
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
                brukernotifikasjonerMeldingType = null,
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
        arbeidsgivernotifikasjonMerkelapp: String? = null,
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
                null,
                arbeidsgivernotifikasjonMerkelapp,
            ),
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
                null,
            ),
        )
    }

    private fun lagreIkkeUtsendtArbeidstakerVarsel(
        kanal: Kanal,
        varselHendelse: ArbeidstakerHendelse,
        eksternReferanse: String,
        feilmelding: String?,
        journalpostId: String? = null,
        brukernotifikasjonerMeldingType: String? = null,
    ) {
        database.storeUtsendtVarselFeilet(
            PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = eksternReferanse,
                arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
                narmesteLederFnr = null,
                orgnummer = varselHendelse.orgnummer,
                hendelsetypeNavn = varselHendelse.type.name,
                arbeidsgivernotifikasjonMerkelapp = null,
                brukernotifikasjonerMeldingType = brukernotifikasjonerMeldingType,
                journalpostId = journalpostId,
                kanal = kanal.name,
                feilmelding = feilmelding,
                utsendtForsokTidspunkt = LocalDateTime.now(),
            ),
        )
    }

    private fun lagreIkkeUtsendtNarmesteLederVarsel(
        kanal: Kanal,
        varselHendelse: NarmesteLederHendelse,
        eksternReferanse: String,
        feilmelding: String?,
        merkelapp: String?,
    ) {
        database.storeUtsendtVarselFeilet(
            PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = eksternReferanse,
                arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
                narmesteLederFnr = varselHendelse.narmesteLederFnr,
                orgnummer = varselHendelse.orgnummer,
                hendelsetypeNavn = varselHendelse.type.name,
                arbeidsgivernotifikasjonMerkelapp = merkelapp,
                brukernotifikasjonerMeldingType = null,
                journalpostId = null,
                kanal = kanal.name,
                feilmelding = feilmelding,
                utsendtForsokTidspunkt = LocalDateTime.now(),
            ),
        )
    }
}

fun List<PUtsendtVarsel>.uferdigstilteVarsler(hendelseType: HendelseType) =
    this
        .sortedByDescending { it.utsendtTidspunkt }
        .filter {
            it.eksternReferanse != null &&
                it.type == hendelseType.toString() &&
                it.ferdigstiltTidspunkt == null
        }
