package no.nav.syfo.service

import no.nav.syfo.kafka.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.varselbus.*
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.varselbus.domain.HendelseType.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselBusService(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,

) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.VarselBusService")
    private val oppfolgingsplanVarsler = OppfolgingsplanVarsler()
    fun processVarselHendelse(varselHendelse: EsyfovarselHendelse) {
        log.info("Sender varsel av type ${varselHendelse.type}")
        when (varselHendelse.type) {
            NL_OPPFOLGINGSPLAN_OPPRETTET -> dineSykmeldteHendelseKafkaProducer.sendHendelse(
                    oppfolgingsplanVarsler.varselOpprettetOppfolgingsplanNL(varselHendelse)
            )
            NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> dineSykmeldteHendelseKafkaProducer.sendHendelse(
                oppfolgingsplanVarsler.varselSendtOppfolgingsplanNL(varselHendelse)
            )
        }
    }
}
