package no.nav.syfo.service

import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import java.net.URL

class SenderFacade(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val brukernotifikasjonerService: BrukernotifikasjonerService,
    val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
) {

    fun sendTilDineSykmeldte(varsel: DineSykmeldteVarsel) {
        dineSykmeldteHendelseKafkaProducer.sendVarsel(varsel)
    }

    fun sendTilBrukernotifikasjoner(uuid: String, mottakerFnr: String, content: String, url:URL) {
        brukernotifikasjonerService.sendVarsel(uuid, mottakerFnr, content, url)
    }

    fun sendTilArbeidsgiverNotifikasjon(
        varsel: ArbeidsgiverNotifikasjonInput
    ) {
        arbeidsgiverNotifikasjonService.sendNotifikasjon(varsel)
    }

}