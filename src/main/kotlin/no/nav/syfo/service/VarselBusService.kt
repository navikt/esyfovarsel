package no.nav.syfo.service

import no.nav.syfo.UrlEnv
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_SVAR_MOTEBEHOV
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_OPPFOLGINGSPLAN_OPPRETTET
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselBusService(
    dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val motebehovVarselService: MotebehovVarselService
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.VarselBusService")
    private val oppfolgingsplanVarselService = OppfolgingsplanVarselService(dineSykmeldteHendelseKafkaProducer)
    suspend fun processVarselHendelse(varselHendelse: EsyfovarselHendelse) {
        log.info("Behandler varsel av type ${varselHendelse.type}")
        when (varselHendelse.type) {
            NL_OPPFOLGINGSPLAN_OPPRETTET,
            NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> oppfolgingsplanVarselService.sendVarselTilDineSykmeldte(varselHendelse)
            NL_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilNarmesteLeder(varselHendelse)
            SM_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilSykmeldt(varselHendelse)
        }
    }
}
