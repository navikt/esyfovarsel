package no.nav.syfo.job

import no.nav.syfo.UrlEnv
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.metrics.tellMerVeiledningVarselSendt
import no.nav.syfo.service.AccessControlService
import no.nav.syfo.service.MerVeiledningVarselFinder
import no.nav.syfo.service.MerVeiledningVarselService
import org.slf4j.LoggerFactory

class SendMerVeiledningVarslerJobb(
    private val merVeiledningVarselFinder: MerVeiledningVarselFinder,
    val accessControlService: AccessControlService,
    val urlEnv: UrlEnv,
    val merVeiledningVarselService: MerVeiledningVarselService,
) {
    private val log = LoggerFactory.getLogger(SendMerVeiledningVarslerJobb::class.java)

    suspend fun sendVarsler(): Int {
        log.info("Starter SendMerVeiledningVarslerJobb")

        var antallVarslerSendt = 0

        val varslerToSendToday = merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday()

        log.info("Planlegger å sende ${varslerToSendToday.size} varsler totalt")

        varslerToSendToday.forEach {
            try {
                val userAccessStatus = accessControlService.getUserAccessStatus(it.fnr)

                merVeiledningVarselService.sendVarselTilArbeidstaker(
                    ArbeidstakerHendelse(
                        HendelseType.SM_MER_VEILEDNING,
                        false,
                        null,
                        it.fnr,
                        null,
                    ),
                    it.uuid,
                    userAccessStatus,
                )

                antallVarslerSendt++
                log.info("Sendt varsel med UUID ${it.uuid}")
            } catch (e: RuntimeException) {
                log.error("Feil i utsending av varsel med UUID: ${it.uuid} | ${e.message}", e)
            }

        }

        log.info("Sendte $antallVarslerSendt varsler")
        tellMerVeiledningVarselSendt(antallVarslerSendt)

        log.info("Avslutter SendMerVeiledningVarslerJobb")

        return antallVarslerSendt
    }
}
