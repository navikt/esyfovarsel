package no.nav.syfo.service

import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.*
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselBusService(
    val motebehovVarselService: MotebehovVarselService,
    val oppfolgingsplanVarselService: OppfolgingsplanVarselService,
    val dialogmoteInnkallingVarselService: DialogmoteInnkallingVarselService,
    val microFrontendService: MicroFrontendService
) {
    private val log: Logger = LoggerFactory.getLogger(VarselBusService::class.qualifiedName)
    fun processVarselHendelse(
        varselHendelse: EsyfovarselHendelse
    ) {
        log.info("Behandler varsel av type ${varselHendelse.type}")
        when (varselHendelse.type) {
            NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> oppfolgingsplanVarselService.sendEllerFerdigstillVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())
            SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> oppfolgingsplanVarselService.sendEllerFerdigstillVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())

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

    fun processVarselHendelseAsMinSideMicrofrontendEvent(event: EsyfovarselHendelse) {
        if (event is ArbeidstakerHendelse) {
            val fnr = event.arbeidstakerFnr
            when (event.type) {
                SM_DIALOGMOTE_INNKALT -> microFrontendService.enableDialogmoteFrontendForFnr(fnr)
                SM_DIALOGMOTE_AVLYST,
                SM_DIALOGMOTE_REFERAT -> microFrontendService.disableDialogmoteFrontendForFnr(fnr)
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
}
