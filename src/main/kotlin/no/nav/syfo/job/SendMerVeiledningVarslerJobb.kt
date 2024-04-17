package no.nav.syfo.job

import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.metrics.tellMerVeiledningVarselSendt
import no.nav.syfo.service.MerVeiledningVarselFinder
import no.nav.syfo.service.MerVeiledningVarselService
import org.slf4j.LoggerFactory

class SendMerVeiledningVarslerJobb(
    private val merVeiledningVarselFinder: MerVeiledningVarselFinder,
    private val merVeiledningVarselService: MerVeiledningVarselService,
) {
    private val log = LoggerFactory.getLogger(SendMerVeiledningVarslerJobb::class.qualifiedName)
    private val logName = "[${SendMerVeiledningVarslerJobb::class.simpleName}]"

    suspend fun sendVarsler(): Int {
        log.info("$logName Starter jobb")

        var antallVarslerSendt = 0

        val varslerToSendToday = merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday()

        log.info("$logName Planlegger å sende ${varslerToSendToday.size} varsler totalt")

        varslerToSendToday.forEach {
            try {
                merVeiledningVarselService.sendVarselTilArbeidstaker(
                    ArbeidstakerHendelse(
                        type = HendelseType.SM_MER_VEILEDNING,
                        ferdigstill = false,
                        data = null,
                        arbeidstakerFnr = it.fnr,
                        orgnummer = null,
                    ),
                    it.uuid,
                )

                antallVarslerSendt++
                log.info("$logName Sendt varsel med UUID ${it.uuid}")
            } catch (e: RuntimeException) {
                log.error("$logName Feil i utsending av varsel med UUID: ${it.uuid} | ${e.message}", e)
            }

        }

        log.info("$logName Sendte $antallVarslerSendt varsler")
        tellMerVeiledningVarselSendt(antallVarslerSendt)

        log.info("$logName Avslutter jobb")

        return antallVarslerSendt
    }
}
