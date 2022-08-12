package no.nav.syfo.service

import no.nav.syfo.*
import no.nav.syfo.kafka.consumers.varselbus.domain.*
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class MotebehovVarselService(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val brukernotifikasjonerService: BrukernotifikasjonerService,
    val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    val dialogmoterUrl: String,
) {
    val WEEKS_BEFORE_DELETE = 4L

    fun sendVarselTilNarmesteLeder(varselHendelse: EsyfovarselHendelse<NarmesteLederMottaker>) {
        sendVarselTilDineSykmeldte(varselHendelse)
        sendVarselTilArbeidsgiverNotifikasjon(varselHendelse)
    }

    fun sendVarselTilSykmeldt(varselHendelse: EsyfovarselHendelse<SykmeldtMottaker>) {
        val url = URL(dialogmoterUrl + BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_URL)
        brukernotifikasjonerService.sendVarsel(
            UUID.randomUUID().toString(),
            varselHendelse.mottaker.mottakerFnr,
            BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST,
            url
        )
    }

    private fun sendVarselTilArbeidsgiverNotifikasjon(varselHendelse: EsyfovarselHendelse<NarmesteLederMottaker>) {
        arbeidsgiverNotifikasjonService.sendNotifikasjon(
            ArbeidsgiverNotifikasjonInput(
                UUID.randomUUID(),
                varselHendelse.mottaker.orgnummer,
                varselHendelse.mottaker.mottakerFnr,
                varselHendelse.mottaker.ansattFnr,
                ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_MESSAGE_TEXT,
                ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_EMAIL_TITLE,
                { url: String -> ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_EMAIL_BODY },
                LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)
            )
        )
    }

    private fun sendVarselTilDineSykmeldte(varselHendelse: EsyfovarselHendelse<NarmesteLederMottaker>) {
        val varseltekst = DINE_SYKMELDTE_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
        val dineSykmeldteVarsel = DineSykmeldteVarsel(
            varselHendelse.mottaker.ansattFnr,
            varselHendelse.mottaker.orgnummer,
            varselHendelse.type.toDineSykmeldteHendelseType().toString(),
            null,
            varseltekst,
            OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)
        )
        dineSykmeldteHendelseKafkaProducer.sendVarsel(dineSykmeldteVarsel)
    }
}
