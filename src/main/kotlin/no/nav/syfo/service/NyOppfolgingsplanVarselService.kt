package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJON_OPPFOLGINGSPLAN_OPPRETTET_MESSAGE_TEXT
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.BESKJED
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID

class NyOppfolgingsplanVarselService(
    private val senderFacade: SenderFacade,
    private val accessControlService: AccessControlService,
    private val nyOppfolgingsplanUrl: String,
) {
    companion object {
        private const val SYKMELDT_PATH = "/sykmeldt"
        private val log = LoggerFactory.getLogger(NyOppfolgingsplanVarselService::class.qualifiedName)
    }

    suspend fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        val eksternVarsling = accessControlService.canUserBeNotifiedByEmailOrSMS(varselHendelse.arbeidstakerFnr)

        when (varselHendelse.type) {
            HendelseType.SM_OPPFOLGINGSPLAN_OPPRETTET ->
                senderFacade.sendTilBrukernotifikasjoner(
                    uuid = UUID.randomUUID().toString(),
                    mottakerFnr = varselHendelse.arbeidstakerFnr,
                    content = BRUKERNOTIFIKASJON_OPPFOLGINGSPLAN_OPPRETTET_MESSAGE_TEXT,
                    url = sykmeldtUrl(),
                    arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
                    orgnummer = varselHendelse.orgnummer,
                    hendelseType = varselHendelse.type.name,
                    eksternVarsling = eksternVarsling,
                    varseltype = BESKJED,
                )

            else -> {
                log.error("NyOppfolgingsplanVarselService støtter ikke hendelsetype ${varselHendelse.type}")
                return
            }
        }
    }

    private fun sykmeldtUrl(): java.net.URL = ("$nyOppfolgingsplanUrl$SYKMELDT_PATH").toURL()

    private fun String.toURL(): java.net.URL = URI(this).toURL()
}
