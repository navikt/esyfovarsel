package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.Kanal.ARBEIDSGIVERNOTIFIKASJON
import no.nav.syfo.db.domain.Kanal.BREV
import no.nav.syfo.db.domain.Kanal.BRUKERNOTIFIKASJON
import no.nav.syfo.db.domain.Kanal.DINE_SYKMELDTE
import no.nav.syfo.db.domain.Kanal.DITT_SYKEFRAVAER
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import java.net.URL
import java.time.LocalDateTime
import java.util.*

class SenderFacade(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val dittSykefravaerMeldingKafkaProducer: DittSykefravaerMeldingKafkaProducer,
    val brukernotifikasjonerService: BrukernotifikasjonerService,
    val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    val fysiskBrevUtsendingService: FysiskBrevUtsendingService,
    val databaseInterface: DatabaseInterface
) {
    fun sendTilDineSykmeldte(
        varselHendelse: NarmesteLederHendelse,
        varsel: DineSykmeldteVarsel
    ) {
        dineSykmeldteHendelseKafkaProducer.sendVarsel(varsel)
        lagreUtsendtNarmesteLederVarsel(DINE_SYKMELDTE, varselHendelse, varsel.id.toString())
    }

    fun sendTilDittSykefravaer(
        varselHendelse: ArbeidstakerHendelse,
        varsel: DittSykefravaerVarsel
    ) {
        val eksternUUID = dittSykefravaerMeldingKafkaProducer.sendMelding(varsel.melding)
        lagreUtsendtArbeidstakerVarsel(DITT_SYKEFRAVAER, varselHendelse, eksternUUID)
    }
    fun sendTilBrukernotifikasjoner(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL,
        varselHendelse: ArbeidstakerHendelse
    ) {
        brukernotifikasjonerService.sendVarsel(uuid, mottakerFnr, content, url)
        lagreUtsendtArbeidstakerVarsel(BRUKERNOTIFIKASJON, varselHendelse, uuid)
    }

    fun sendTilArbeidsgiverNotifikasjon(
        varselHendelse: NarmesteLederHendelse,
        varsel: ArbeidsgiverNotifikasjonInput
    ) {
        arbeidsgiverNotifikasjonService.sendNotifikasjon(varsel)
        lagreUtsendtNarmesteLederVarsel(ARBEIDSGIVERNOTIFIKASJON, varselHendelse, varsel.uuid.toString())
    }

    fun sendBrevTilFysiskPrint(
        fnr: String,
        uuid: String,
        varselHendelse: ArbeidstakerHendelse
    ) {
        fysiskBrevUtsendingService.sendBrev(fnr, uuid)
        lagreUtsendtArbeidstakerVarsel(BREV, varselHendelse, uuid)
    }

    fun lagreUtsendtNarmesteLederVarsel(
        kanal: Kanal,
        varselHendelse: NarmesteLederHendelse,
        eksternReferanse: String
    ) {
        databaseInterface.storeUtsendtVarsel(
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
            )
        )
    }

    fun lagreUtsendtArbeidstakerVarsel(
        kanal: Kanal,
        varselHendelse: ArbeidstakerHendelse,
        eksternReferanse: String
    ) {
        databaseInterface.storeUtsendtVarsel(
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
            )
        )
    }
}
