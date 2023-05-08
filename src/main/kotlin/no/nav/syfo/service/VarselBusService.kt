package no.nav.syfo.service

import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.*
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselBusService(
    val senderFacade: SenderFacade,
    val motebehovVarselService: MotebehovVarselService,
    val oppfolgingsplanVarselService: OppfolgingsplanVarselService,
    val dialogmoteInnkallingVarselService: DialogmoteInnkallingVarselService,
    val mikrofrontendService: MikrofrontendService
) {
    private val log: Logger = LoggerFactory.getLogger(VarselBusService::class.qualifiedName)
    fun processVarselHendelse(
        varselHendelse: EsyfovarselHendelse
    ) {
        log.info("Behandler varsel av type ${varselHendelse.type}")
        if (varselHendelse.skalFerdigstilles()) {
            ferdigstillVarsel(varselHendelse)
        } else {
            when (varselHendelse.type) {
                NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> oppfolgingsplanVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())
                SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> oppfolgingsplanVarselService.sendVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())

                NL_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())
                SM_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())

                NL_DIALOGMOTE_INNKALT,
                NL_DIALOGMOTE_AVLYST,
                NL_DIALOGMOTE_REFERAT,
                NL_DIALOGMOTE_NYTT_TID_STED -> dialogmoteInnkallingVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())

                SM_DIALOGMOTE_INNKALT,
                SM_DIALOGMOTE_AVLYST,
                SM_DIALOGMOTE_REFERAT,
                SM_DIALOGMOTE_NYTT_TID_STED,
                SM_DIALOGMOTE_LEST -> dialogmoteInnkallingVarselService.sendVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())

                else -> {
                    log.warn("Klarte ikke mappe varsel av type ${varselHendelse.type} ved behandling forsøk")
                }
            }
        }
    }

    fun ferdigstillVarsel(varselHendelse: EsyfovarselHendelse) {
        if (varselHendelse.isArbeidstakerHendelse()) {
            senderFacade.ferdigstillBrukernotifkasjonVarsler(varselHendelse.toArbeidstakerHendelse())
        } else {
            senderFacade.ferdigstillDineSykmeldteVarsler(
                varselHendelse.toNarmestelederHendelse()
            )
            senderFacade.ferdigstillArbeidsgiverNotifikasjoner(
                varselHendelse.toNarmestelederHendelse(),
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP
            )
        }
    }

    fun processVarselHendelseAsMinSideMicrofrontendEvent(event: EsyfovarselHendelse) {
        if (event is ArbeidstakerHendelse) {
            log.info("Toggling min-side frontend")
            when (event.type) {
                SM_DIALOGMOTE_INNKALT -> mikrofrontendService.enableDialogmoteFrontendForUser(event)
                SM_DIALOGMOTE_NYTT_TID_STED -> mikrofrontendService.updateDialogmoteFrontendForUser(event)
                SM_DIALOGMOTE_AVLYST,
                SM_DIALOGMOTE_REFERAT -> mikrofrontendService.disableDialogmoteFrontendForUser(event)
                else -> return
            }
        }
    }

    private fun EsyfovarselHendelse.toNarmestelederHendelse(): NarmesteLederHendelse {
        return if (this is NarmesteLederHendelse) {
            this
        } else throw IllegalArgumentException("Wrong type of EsyfovarselHendelse, should be of type NarmesteLederHendelse")
    }

    private fun EsyfovarselHendelse.toArbeidstakerHendelse(): ArbeidstakerHendelse {
        return if (this is ArbeidstakerHendelse) {
            this
        } else throw IllegalArgumentException("Wrong type of EsyfovarselHendelse, should be of type ArbeidstakerHendelse")
    }

    private fun EsyfovarselHendelse.isArbeidstakerHendelse(): Boolean {
        return this is ArbeidstakerHendelse
    }
    
    private fun EsyfovarselHendelse.skalFerdigstilles() =
        data?.let { data ->
            val varseldata = data.toVarselData()
            varseldata.status?.ferdigstilt
        } ?: false
}
