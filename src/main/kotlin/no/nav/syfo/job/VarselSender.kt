package no.nav.syfo.job

import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.metrics.tellMerVeiledningVarselSendt
import no.nav.syfo.service.MerVeiledningVarselFinder
import no.nav.syfo.service.MerVeiledningVarselService
import org.slf4j.LoggerFactory

class VarselSender(
    private val merVeiledningVarselFinder: MerVeiledningVarselFinder,
    private val merVeiledningVarselService: MerVeiledningVarselService,
) {
    private val log = LoggerFactory.getLogger(VarselSender::class.java)

    suspend fun sendVarsler(): Int {
        log.info("Starter SendVarslerJobb")

        var antallVarslerSendt = 0

        val varslerToSendToday = merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday()

        log.info("Planlegger å sende ${varslerToSendToday.size} varsler totalt")

        varslerToSendToday.forEach {
            try {
                merVeiledningVarselService.sendVarselTilArbeidstaker(
                    ArbeidstakerHendelse(
                        HendelseType.SM_MER_VEILEDNING,
                        false,
                        null,
                        it.fnr,
                        null,
                    ),
                    it.uuid,
                )

                antallVarslerSendt++
                log.info("Sendt varsel med UUID ${it.uuid}")
            } catch (e: RuntimeException) {
                log.error("Feil i utsending av varsel med UUID: ${it.uuid} | ${e.message}", e)
            }

        }

        log.info("Sendte $antallVarslerSendt varsler")
        tellMerVeiledningVarselSendt(antallVarslerSendt)

        log.info("Avslutter SendVarslerJobb")

        return antallVarslerSendt
    }
}
