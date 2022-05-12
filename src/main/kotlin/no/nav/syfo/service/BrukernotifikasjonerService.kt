package no.nav.syfo.service

import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.kafka.brukernotifikasjoner.BeskjedKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BrukernotifikasjonerService(
    val beskjedKafkaProducer: BeskjedKafkaProducer,
    val accessControl: AccessControl
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.BrukernotifikasjonerService")

    fun sendVarsel(uuid: String, mottakerFnr: String, type: String, content: String): String {
        // Recheck if user can be notified in case of recent 'Addressesperre'
        return try {
            val fodselnummer = accessControl.getFnrIfUserCanBeNotified(mottakerFnr)
            fodselnummer?.let { fnr ->
                beskjedKafkaProducer.sendBeskjed(fnr, content, uuid)
                type
            } ?: UTSENDING_FEILET
        } catch (e: RuntimeException) {
            log.error("Feil i utsending av varsel med UUID: ${uuid} | ${e.message}", e)
            UTSENDING_FEILET
        }
    }

}
