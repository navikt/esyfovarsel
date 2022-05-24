package no.nav.syfo.service

import no.nav.syfo.UrlEnv
import no.nav.syfo.kafka.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.varselbus.domain.HendelseType.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselBusService(
    dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    brukernotifikasjonerService: BrukernotifikasjonerService,
    urlEnv: UrlEnv,
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.VarselBusService")
    private val oppfolgingsplanVarselService = OppfolgingsplanVarselService(dineSykmeldteHendelseKafkaProducer)
    private val motebehovVarselService = MotebehovVarselService(dineSykmeldteHendelseKafkaProducer, brukernotifikasjonerService, urlEnv.dialogmoterUrl)
    fun processVarselHendelse(varselHendelse: EsyfovarselHendelse) {
        log.info("Behandler varsel av type ${varselHendelse.type}")
        when (varselHendelse.type) {
            NL_OPPFOLGINGSPLAN_OPPRETTET,
            NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> oppfolgingsplanVarselService.sendVarselTilDineSykmeldte(varselHendelse)
            NL_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilDineSykmeldte(varselHendelse)
            SM_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilSykmeldt(varselHendelse)
        }
    }
}
