package no.nav.syfo.service

import no.nav.syfo.kafka.producers.brukernotifikasjoner.BeskjedKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

class BrukernotifikasjonerService(
    val beskjedKafkaProducer: BeskjedKafkaProducer,
    val accessControlService: AccessControlService,
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.BrukernotifikasjonerService")

    fun sendVarsel(uuid: String, mottakerFnr: String, content: String, url: URL) {
        // Recheck if user can be notified in case of recent 'Addressesperre'
        if (accessControlService.getUserAccessStatus(mottakerFnr).canUserBeDigitallyNotified) {
            beskjedKafkaProducer.sendBeskjed(mottakerFnr, content, uuid, url)
            log.info("Har sendt melding med uuid $uuid til brukernotifikasjoner: $content")
        } else {
            log.info("Kan ikke sende melding til bruker for melding med uuid $uuid, dette kan skyldes adressesperre")
        }
    }

}
