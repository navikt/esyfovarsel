package no.nav.syfo.service

import no.nav.syfo.*
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.*
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

const val WEEKS_BEFORE_DELETE = 4L

class OppfolgingsplanVarselService(
    val senderFacade: SenderFacade,
    val oppfolgingsplanerUrl: String
) {
    private val objectMapper = createObjectMapper()

    fun sendEllerFerdigstillVarselTilArbeidstaker(
        varselHendelse: ArbeidstakerHendelse
    ) {
        if (varselHendelse.skalFerdigstilles()) {
            ferdigstillVarselArbeidstaker(varselHendelse)
        } else {
            sendVarselTilArbeidstaker(varselHendelse)
        }
    }

    fun sendEllerFerdigstillVarselTilNarmesteLeder(
        varselHendelse: NarmesteLederHendelse
    ) {
        if (varselHendelse.skalFerdigstilles()) {
            ferdigstillVarselNarmesteLeder(varselHendelse)
        } else {
            sendVarselTilNarmesteLeder(varselHendelse)
        }
    }

    private fun sendVarselTilArbeidstaker(
        varselHendelse: ArbeidstakerHendelse
    ) {
        senderFacade.sendTilBrukernotifikasjoner(
            UUID.randomUUID().toString(),
            varselHendelse.arbeidstakerFnr,
            BRUKERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_MESSAGE_TEXT,
            URL(oppfolgingsplanerUrl + BRUKERNOTIFIKASJONER_OPPFOLGINGSPLANER_SYKMELDT_URL),
            varselHendelse
        )
    }

    fun sendVarselTilNarmesteLeder(
        varselHendelse: NarmesteLederHendelse
    ) {
        senderFacade.sendTilDineSykmeldte(
            varselHendelse,
            DineSykmeldteVarsel(
                ansattFnr = varselHendelse.arbeidstakerFnr,
                orgnr = varselHendelse.orgnummer,
                oppgavetype = varselHendelse.type.toDineSykmeldteHendelseType().toString(),
                lenke = null,
                tekst = DINE_SYKMELDTE_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST,
                utlopstidspunkt = OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)
            )
        )
        senderFacade.sendTilArbeidsgiverNotifikasjon(
            varselHendelse,
            ArbeidsgiverNotifikasjonInput(
                UUID.randomUUID(),
                varselHendelse.orgnummer,
                varselHendelse.narmesteLederFnr,
                varselHendelse.arbeidstakerFnr,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_MESSAGE_TEXT,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_EMAIL_TITLE,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_EMAIL_BODY,
                LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)
            )
        )
    }

    private fun ferdigstillVarselArbeidstaker(varselHendelse: ArbeidstakerHendelse) =
        senderFacade.ferdigstillBrukernotifkasjonVarsler(varselHendelse)

    private fun ferdigstillVarselNarmesteLeder(varselHendelse: NarmesteLederHendelse) {
        senderFacade.ferdigstillDineSykmeldteVarsler(
            varselHendelse
        )
        senderFacade.ferdigstillArbeidsgiverNotifikasjoner(
            varselHendelse,
            ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP
        )
    }

    private fun EsyfovarselHendelse.skalFerdigstilles(): Boolean {
        val data = this.data?.toString()
        return data?.let {
            objectMapper.readValue(
                it,
                VarselData::class.java
            )?.status?.ferdigstilt
        } ?: false
    }
}
