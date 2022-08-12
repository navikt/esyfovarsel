package no.nav.syfo.service

import no.nav.syfo.kafka.consumers.varselbus.domain.*
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.*
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselBusService(
    dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val motebehovVarselService: MotebehovVarselService
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.VarselBusService")
    private val oppfolgingsplanVarselService = OppfolgingsplanVarselService(dineSykmeldteHendelseKafkaProducer)
    fun processVarselHendelse(varselHendelse: EsyfovarselHendelse<out Mottaker>) {
        log.info("Behandler varsel av type ${varselHendelse.type}")
        when (varselHendelse.type) {
            NL_OPPFOLGINGSPLAN_OPPRETTET,
            NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> oppfolgingsplanVarselService.sendVarselTilDineSykmeldte(varselHendelse.toNarmestelederHendelse())
            NL_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilNarmesteLeder(varselHendelse.toNarmestelederHendelse())
            SM_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilSykmeldt(varselHendelse.toSykmeldtHendelse())
        }
    }

    private fun EsyfovarselHendelse<out Any>.toNarmestelederHendelse(): EsyfovarselHendelse<NarmesteLederMottaker> {
        return if (mottaker is NarmesteLederMottaker) {
            this as EsyfovarselHendelse<NarmesteLederMottaker>
        } else {
            throw IllegalArgumentException("Wrong type of EsyfovarselHendelse, should be of type NarmesteLederHendelse")
        }
    }

    private fun EsyfovarselHendelse<out Any>.toSykmeldtHendelse(): EsyfovarselHendelse<SykmeldtMottaker> {
        return if (mottaker is SykmeldtMottaker) {
            this as EsyfovarselHendelse<SykmeldtMottaker>
        } else {
            throw IllegalArgumentException("Wrong type of EsyfovarselHendelse, should be of type NarmesteLederHendelse")
        }
    }


}
