package no.nav.syfo.service

import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.kafka.brukernotifikasjoner.BeskjedKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.RuntimeException

class SendVarselService(
    val beskjedKafkaProducer: BeskjedKafkaProducer,
    val accessControl: AccessControl
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.db.SendVarselService")

    fun sendVarsel(pPlanlagtVarsel: PPlanlagtVarsel): String {
        // Recheck if user can be notified in case of recent 'Addressesperre'
        try {
            val fodselnummer = accessControl.getFnrIfUserCanBeNotified(pPlanlagtVarsel.aktorId)
            val uuid = pPlanlagtVarsel.uuid
            fodselnummer?.let { fnr ->
                varselContentFromType(pPlanlagtVarsel.type)?.let { content ->
                    beskjedKafkaProducer.sendBeskjed(fnr, content, uuid)
                } ?: throw RuntimeException("Klarte ikke mappe typestreng til innholdstekst")
            }
        } catch (e: RuntimeException) {
            log.error("Feil i utsending av varsel med UUID: ${pPlanlagtVarsel.uuid} | ${e.message}")
            return "Feil"
        }
        return pPlanlagtVarsel.type
    }

    private fun varselContentFromType(type: String): String? {
        return when (type) {
            VarselType.AKTIVITETSKRAV.toString() -> "NAV skal nå vurdere aktivitetsplikten din"
            VarselType.MER_VEILEDNING.toString() -> "Det nærmer seg datoen da du ikke lenger kan få sykepenger. Gå til Ditt Sykefravær og se hva du kan gjøre."
            else -> null
        }
    }
}
