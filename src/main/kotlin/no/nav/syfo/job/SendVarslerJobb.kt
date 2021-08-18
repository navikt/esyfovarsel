package no.nav.syfo.job

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.fetchPlanlagtVarselByUtsendingsdato
import no.nav.syfo.varsel.VarselSender
import org.slf4j.LoggerFactory
import java.time.LocalDate

class SendVarslerJobb(
    private val databaseAccess: DatabaseInterface,
    private val varselSender: VarselSender
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.job.SendVarslerJobb")

    fun sendVarsler() {
        log.info("Starter SendVarslerJobb")

        val varslerToSendToday = databaseAccess.fetchPlanlagtVarselByUtsendingsdato(LocalDate.now())
        log.info("Planlegger å sende ${varslerToSendToday.size} i dag")

        varslerToSendToday.forEach {
            varselSender.send(it)
        }

        log.info("Avslutter SendVarslerJobb")

    }
}
