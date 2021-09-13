package no.nav.syfo.job

import no.nav.syfo.Toggles
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deletePlanlagtVarselByVarselId
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByUtsendingsdato
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.metrics.tellAktivitetskravVarselSendt
import no.nav.syfo.metrics.tellMerVeiledningVarselSendt
import no.nav.syfo.service.SendVarselService
import org.slf4j.LoggerFactory
import java.time.LocalDate

class SendVarslerJobb(
    private val databaseAccess: DatabaseInterface,
    private val sendVarselService: SendVarselService,
    private val toggles: Toggles
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.job.SendVarslerJobb")

    fun sendVarsler() {
        log.info("Starter SendVarslerJobb")

        val varslerToSendToday = databaseAccess.fetchPlanlagtVarselByUtsendingsdato(LocalDate.now())
        log.info("Planlegger å sende ${varslerToSendToday.size} varsler")

        if (!toggles.sendAktivitetskravVarsler) log.info("Utsending av Aktivitetskrav er ikke aktivert, og varsler av denne typen blir ikke sendt")
        if (!toggles.sendMerVeiledningVarsler) log.info("Utsending av Mer veiledning er ikke aktivert, og varsler av denne typen blir ikke sendt")

        val varslerSendt = HashMap<String, Int>()
        varslerToSendToday.forEach {
            if (skalSendeVarsel(it)) {
                log.info("Sender varsel med UUID ${it.uuid}")
                val type = sendVarselService.sendVarsel(it)
                incrementVarselCountMap(varslerSendt, type)
                if (toggles.markerVarslerSomSendt) {
                    log.info("Markerer varsel med UUID ${it.uuid} som sendt")
                    databaseAccess.storeUtsendtVarsel(it)
                    databaseAccess.deletePlanlagtVarselByVarselId(it.uuid)
                }
            }
        }

        varslerSendt.forEach { (key, value) ->
            log.info("Sendte $value varsler av type $key")
        }

        varslerSendt[VarselType.MER_VEILEDNING.name]?.let(::tellMerVeiledningVarselSendt)
        varslerSendt[VarselType.AKTIVITETSKRAV.name]?.let(::tellAktivitetskravVarselSendt)

        log.info("Avslutter SendVarslerJobb")
    }

    private fun incrementVarselCountMap(map: HashMap<String, Int>, type: String) {
        val count = (map[type] ?: 0) + 1
        map[type] = count
    }

    private fun skalSendeVarsel(it: PPlanlagtVarsel) = (it.type.equals(VarselType.MER_VEILEDNING.name) && toggles.sendMerVeiledningVarsler) ||
            (it.type.equals(VarselType.AKTIVITETSKRAV.name) && toggles.sendAktivitetskravVarsler)
}
