package no.nav.syfo.service

import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_AVLYST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_INNKALT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_MOTEBEHOV_TILBAKEMELDING
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_NYTT_TID_STED
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_REFERAT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_SVAR_MOTEBEHOV
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_AKTIVITETSPLIKT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_ARBEIDSUFORHET_FORHANDSVARSEL
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_AVLYST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_INNKALT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_LEST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_MOTEBEHOV_TILBAKEMELDING
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_NYTT_TID_STED
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_REFERAT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_MER_VEILEDNING
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_FORHANDSVARSEL_MANGLENDE_MEDVIRKNING
import no.nav.syfo.kafka.consumers.varselbus.domain.isArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.skalFerdigstilles
import no.nav.syfo.kafka.consumers.varselbus.domain.toArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toNarmestelederHendelse
import no.nav.syfo.service.microfrontend.MikrofrontendService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselBusService(
    val senderFacade: SenderFacade,
    private val motebehovVarselService: MotebehovVarselService,
    private val oppfolgingsplanVarselService: OppfolgingsplanVarselService,
    private val dialogmoteInnkallingVarselService: DialogmoteInnkallingVarselService,
    private val aktivitetspliktForhandsvarselVarselService: AktivitetspliktForhandsvarselVarselService,
    private val arbeidsuforhetForhandsvarselService: ArbeidsuforhetForhandsvarselService,
    private val mikrofrontendService: MikrofrontendService,
    private val friskmeldingTilArbeidsformidlingVedtakService: FriskmeldingTilArbeidsformidlingVedtakService,
    private val manglendeMedvirkningVarselService: ManglendeMedvirkningVarselService,
    private val merVeiledningVarselService: MerVeiledningVarselService
) {
    private val log: Logger = LoggerFactory.getLogger(VarselBusService::class.qualifiedName)
    suspend fun processVarselHendelse(
        varselHendelse: EsyfovarselHendelse,
    ) {
       if (varselHendelse.skalFerdigstilles()) {
            ferdigstillVarsel(varselHendelse)
        } else {
            when (varselHendelse.type) {
                NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> oppfolgingsplanVarselService.sendVarselTilNarmesteLeder(
                    varselHendelse.toNarmestelederHendelse()
                )

                SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> oppfolgingsplanVarselService.sendVarselTilArbeidstaker(
                    varselHendelse.toArbeidstakerHendelse()
                )

                NL_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())
                SM_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())
                NL_DIALOGMOTE_MOTEBEHOV_TILBAKEMELDING -> motebehovVarselService.sendMotebehovTilbakemeldingTilNarmesteLeder(
                    varselHendelse.toNarmestelederHendelse()
                )

                SM_DIALOGMOTE_MOTEBEHOV_TILBAKEMELDING -> motebehovVarselService.sendMotebehovTilbakemeldingTilArbeidstaker(
                    varselHendelse.toArbeidstakerHendelse()
                )

                NL_DIALOGMOTE_INNKALT,
                NL_DIALOGMOTE_AVLYST,
                NL_DIALOGMOTE_REFERAT,
                NL_DIALOGMOTE_NYTT_TID_STED,
                -> dialogmoteInnkallingVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())

                SM_DIALOGMOTE_INNKALT,
                SM_DIALOGMOTE_AVLYST,
                SM_DIALOGMOTE_REFERAT,
                SM_DIALOGMOTE_NYTT_TID_STED,
                SM_DIALOGMOTE_LEST,
                -> dialogmoteInnkallingVarselService.sendVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())

                SM_AKTIVITETSPLIKT -> aktivitetspliktForhandsvarselVarselService.sendVarselTilArbeidstaker(
                    varselHendelse.toArbeidstakerHendelse()
                )

                SM_ARBEIDSUFORHET_FORHANDSVARSEL -> arbeidsuforhetForhandsvarselService.sendVarselTilArbeidstaker(
                    varselHendelse.toArbeidstakerHendelse()
                )

                SM_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING -> friskmeldingTilArbeidsformidlingVedtakService.sendVarselTilArbeidstaker(
                    varselHendelse.toArbeidstakerHendelse()
                )

                SM_MER_VEILEDNING -> merVeiledningVarselService.sendVarselTilArbeidstaker(
                    varselHendelse.toArbeidstakerHendelse()
                )

                SM_FORHANDSVARSEL_MANGLENDE_MEDVIRKNING ->
                    manglendeMedvirkningVarselService.sendVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())

                else -> {
                    log.warn("Klarte ikke mappe varsel av type ${varselHendelse.type} ved behandling forsøk")
                }
            }
        }
    }

    suspend fun ferdigstillVarsel(varselHendelse: EsyfovarselHendelse) {
        if (varselHendelse.isArbeidstakerHendelse()) {
            senderFacade.ferdigstillArbeidstakerVarsler(varselHendelse.toArbeidstakerHendelse())
        } else {
            senderFacade.ferdigstillNarmesteLederVarsler(varselHendelse.toNarmestelederHendelse())
        }
    }

    fun processVarselHendelseAsMinSideMicrofrontendEvent(event: EsyfovarselHendelse) {
        if (event.isArbeidstakerHendelse()) {
            val arbeidstakerHendelse = event.toArbeidstakerHendelse()
            try {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelse)
            } catch (e: RuntimeException) {
                log.error("Fikk feil under oppdatering av mikrofrontend state: ${e.message}", e)
            }
        }
    }
}
