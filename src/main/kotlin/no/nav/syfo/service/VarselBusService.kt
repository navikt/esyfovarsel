package no.nav.syfo.service

import no.nav.syfo.kafka.consumers.varselbus.domain.*
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.*
import org.slf4j.*

class VarselBusService(
    val motebehovVarselService: MotebehovVarselService,
    val oppfolgingsplanVarselService: OppfolgingsplanVarselService,
    val dialogmoteInnkallingVarselService: DialogmoteInnkallingVarselService
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.VarselBusService")
    fun processVarselHendelse(varselHendelse: EsyfovarselHendelse) {
        log.info("Behandler varsel av type ${varselHendelse.type}")
        when (varselHendelse.type) {
            NL_OPPFOLGINGSPLAN_OPPRETTET,
            NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> oppfolgingsplanVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())

            NL_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())
            SM_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())

            NL_DIALOGMOTE_INNKALT -> dialogmoteInnkallingVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())
            SM_DIALOGMOTE_INNKALT -> dialogmoteInnkallingVarselService.sendVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())
            NL_DIALOGMOTE_AVLYST -> dialogmoteInnkallingVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())
            SM_DIALOGMOTE_AVLYST -> dialogmoteInnkallingVarselService.sendVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())
            NL_DIALOGMOTE_REFERAT -> dialogmoteInnkallingVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())
            SM_DIALOGMOTE_REFERAT -> dialogmoteInnkallingVarselService.sendVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())
            NL_DIALOGMOTE_NYTT_TID_STED -> dialogmoteInnkallingVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())
            SM_DIALOGMOTE_NYTT_TID_STED -> dialogmoteInnkallingVarselService.sendVarselTilArbeidstaker(varselHendelse.toArbeidstakerHendelse())
            else -> {
                log.warn("Klarte ikke mappe varsel av type ${varselHendelse.type} ved behandling forsøk")
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
