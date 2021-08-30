package no.nav.syfo.job

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deletePlanlagtVarselByVarselId
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByUtsendingsdato
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.varsel.VarselSender
import org.slf4j.LoggerFactory
import java.time.LocalDate

class SendVarslerJobb(
    private val databaseAccess: DatabaseInterface,
    private val varselSender: VarselSender,
    private val markerVarslerSomSendt: Boolean,
    private val skalSendeMerVeiledningVarsel: Boolean,
    private val skalSendeAktivitetskravVarsel: Boolean
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.job.SendVarslerJobb")

    fun sendVarsler() {
        log.info("Starter SendVarslerJobb")

        val varslerToSendToday = databaseAccess.fetchPlanlagtVarselByUtsendingsdato(LocalDate.now())
        log.info("Planlegger å sende ${varslerToSendToday.size} varsler i dag")

        varslerToSendToday.forEach {
            if(skalSendeVarsel(it)){
                log.info("Sender varsel $it")
                varselSender.send(it)
                if (markerVarslerSomSendt) {
                    log.info("Markerer varsel som sendt $it")
                    databaseAccess.storeUtsendtVarsel(it)
                    databaseAccess.deletePlanlagtVarselByVarselId(it.uuid)
                }
            } else {
                log.info("Varsel ble ikke sendt fordi utsending av ${it.type} ikke er aktivert")
            }
        }

        log.info("Avslutter SendVarslerJobb")

    }

    private fun skalSendeVarsel(it: PPlanlagtVarsel) = (it.type.equals(VarselType.MER_VEILEDNING.name) && skalSendeMerVeiledningVarsel) ||
            (it.type.equals(VarselType.AKTIVITETSKRAV.name) && skalSendeAktivitetskravVarsel)
}
