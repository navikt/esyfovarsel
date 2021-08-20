package no.nav.syfo.job

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deletePlanlagtVarselByVarselId
import no.nav.syfo.db.fetchPlanlagtVarselByUtsendingsdato
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.varsel.VarselSender
import org.slf4j.LoggerFactory
import java.time.LocalDate

class SendVarslerJobb(
    private val databaseAccess: DatabaseInterface,
    private val varselSender: VarselSender,
    private val markerVarslerSomSendt: Boolean
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.job.SendVarslerJobb")

    fun sendVarsler() {
        log.info("Starter SendVarslerJobb")

        val varslerToSendToday = databaseAccess.fetchPlanlagtVarselByUtsendingsdato(LocalDate.now())
        log.info("Planlegger å sende ${varslerToSendToday.size} varsler i dag")

        varslerToSendToday.forEach {
            varselSender.send(it)
            if (markerVarslerSomSendt) {
                databaseAccess.storeUtsendtVarsel(it)
                databaseAccess.deletePlanlagtVarselByVarselId(it.uuid)
            }
        }

        log.info("Avslutter SendVarslerJobb")

    }
}
