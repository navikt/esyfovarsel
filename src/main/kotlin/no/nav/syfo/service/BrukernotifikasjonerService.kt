package no.nav.syfo.service

import no.nav.syfo.kafka.brukernotifikasjoner.BeskjedKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

class BrukernotifikasjonerService(
    val beskjedKafkaProducer: BeskjedKafkaProducer,
    val accessControl: AccessControl,
    val url: URL
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.BrukernotifikasjonerService")

    fun sendVarsel(uuid: String, mottakerFnr: String, content: String) {
        // Recheck if user can be notified in case of recent 'Addressesperre'
        val fodselnummer = accessControl.getFnrIfUserCanBeNotifiedFromFnr(mottakerFnr)
        fodselnummer?.let { fnr ->
            beskjedKafkaProducer.sendBeskjed(fnr, content, uuid, url)
        } ?: log.info("Kan ikke sende melding til bruker for melding med uuid ${uuid}, dette kan skyldes adressesperre")
    }

}
