package no.nav.syfo.service

import no.nav.syfo.*
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

const val WEEKS_BEFORE_DELETE = 4L

class OppfolgingsplanVarselService(
    val senderFacade: SenderFacade,
    val accessControlService: AccessControlService,
    val oppfolgingsplanerUrl: String
) {
    fun sendVarselTilArbeidstaker(
        varselHendelse: ArbeidstakerHendelse
    ) {
        if (accessControlService.canUserBeNotifiedByEmailOrSMS(varselHendelse.arbeidstakerFnr)) {
            varsleArbeidstakerViaBrukernotifikasjoner(varselHendelse, true)
        } else {
            varsleArbeidstakerViaBrukernotifikasjoner(varselHendelse, false)
        }
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

    private fun varsleArbeidstakerViaBrukernotifikasjoner(
        varselHendelse: ArbeidstakerHendelse,
        eksternVarsling: Boolean,
    ) {
        senderFacade.sendTilBrukernotifikasjoner(
            UUID.randomUUID().toString(),
            varselHendelse.arbeidstakerFnr,
            BRUKERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_MESSAGE_TEXT,
            URL(oppfolgingsplanerUrl + BRUKERNOTIFIKASJONER_OPPFOLGINGSPLANER_SYKMELDT_URL),
            varselHendelse,
            eksternVarsling = eksternVarsling
        )
    }
}
