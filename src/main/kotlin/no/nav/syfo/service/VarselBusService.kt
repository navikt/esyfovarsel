package no.nav.syfo.service

import no.nav.syfo.kafka.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.varselbus.*
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.varselbus.domain.HendelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

class VarselBusService(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val accessControl: AccessControl
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.VarselBusService")


    fun processVarselHendelse(varselHendelse: EsyfovarselHendelse) {
        when (varselHendelse.type) {
            HendelseType.NL_OPPFOLGINGSPLAN_OPPRETTET -> opprettetOppfolgingsplanNL(varselHendelse)
            else -> sendtOppfolgingsplanNL(varselHendelse)
        }
    }

    private fun sendtOppfolgingsplanNL(varselHendelse: EsyfovarselHendelse) {
        val varseldata = varselHendelse.dataToOppfolgingsplanNLVarselData()
        val dineSykmeldteVarsel = DineSykmeldteVarsel(
            varseldata.ansattFnr,
            varseldata.orgnummer,
            varselHendelse.type.toDineSykmeldteHendelse().toString(),
            null,
            NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST
        )
        log.info("Sender varsel av type ${varselHendelse.type}")
        dineSykmeldteHendelseKafkaProducer.sendHendelse(dineSykmeldteVarsel)

    }

    private fun opprettetOppfolgingsplanNL(varselHendelse: EsyfovarselHendelse) {
        val varseldata = varselHendelse.dataToOppfolgingsplanNLVarselData()
        val dineSykmeldteVarsel = DineSykmeldteVarsel(
            varseldata.ansattFnr,
            varseldata.orgnummer,
            varselHendelse.type.toDineSykmeldteHendelse().toString(),
            null,
            NL_OPPFOLGINGSPLAN_OPPRETTET_TEKST
        )
        log.info("Sender varsel av type ${varselHendelse.type}")
        dineSykmeldteHendelseKafkaProducer.sendHendelse(dineSykmeldteVarsel)
    }

}