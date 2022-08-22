package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.Kanal.ARBEIDSGIVERNOTIFIKASJON
import no.nav.syfo.db.domain.Kanal.DINE_SYKMELDTE
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import java.net.URL
import java.time.LocalDateTime
import java.util.*

class SenderFacade(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val brukernotifikasjonerService: BrukernotifikasjonerService,
    val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    val databaseInterface: DatabaseInterface
) {

    fun sendTilDineSykmeldte(varselHendelse: NarmesteLederHendelse, varsel: DineSykmeldteVarsel) {
        dineSykmeldteHendelseKafkaProducer.sendVarsel(varsel)
        lagreUtsendtNarmesteLederVarsel(DINE_SYKMELDTE, varselHendelse)
    }

    fun sendTilBrukernotifikasjoner(varselHendelse: ArbeidstakerHendelse, uuid: String, mottakerFnr: String, content: String, url: URL) {
        brukernotifikasjonerService.sendVarsel(uuid, mottakerFnr, content, url)
        lagreUtsendtArbeidstakerVarsel(Kanal.BRUKERNOTIFIKASJON, varselHendelse)
    }

    fun sendTilArbeidsgiverNotifikasjon(
        varselHendelse: NarmesteLederHendelse,
        varsel: ArbeidsgiverNotifikasjonInput
    ) {
        arbeidsgiverNotifikasjonService.sendNotifikasjon(varsel)
        lagreUtsendtNarmesteLederVarsel(ARBEIDSGIVERNOTIFIKASJON, varselHendelse)
    }

    fun lagreUtsendtNarmesteLederVarsel(kanal: Kanal, varselHendelse: NarmesteLederHendelse) {
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
                null
            )
        )
    }

    fun lagreUtsendtArbeidstakerVarsel(kanal: Kanal, varselHendelse: ArbeidstakerHendelse) {
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
                null
            )
        )
    }
}
