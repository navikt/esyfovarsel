package no.nav.syfo.service

import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

class BrukernotifikasjonerService(
    val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
    val accessControlService: AccessControlService,
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.BrukernotifikasjonerService")

    fun sendVarsel(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL,
        meldingType: BrukernotifikasjonKafkaProducer.MeldingType?
    ) {
        // Recheck if user can be notified in case of recent 'Addressesperre'
        if (accessControlService.getUserAccessStatus(mottakerFnr).canUserBeDigitallyNotified) {
            when (meldingType) {
                BrukernotifikasjonKafkaProducer.MeldingType.BESKJED -> {
                    brukernotifikasjonKafkaProducer.sendBeskjed(mottakerFnr, content, uuid, url)
                    log.info("Har sendt beskjed med uuid $uuid til brukernotifikasjoner: $content")
                }

                BrukernotifikasjonKafkaProducer.MeldingType.OPPGAVE -> {
                    brukernotifikasjonKafkaProducer.sendOppgave(mottakerFnr, content, uuid, url)
                    log.info("Har sendt oppgave med uuid $uuid til brukernotifikasjoner: $content")
                }

                BrukernotifikasjonKafkaProducer.MeldingType.DONE -> {
                    ferdigstillVarsel(uuid, mottakerFnr)
                }
            }
        } else {
            log.info("Kan ikke sende melding til bruker for melding med uuid $uuid, dette kan skyldes adressesperre")
        }
    }

    fun ferdigstillVarsel(
        uuid: String,
        mottakerFnr: String
    ) {
        brukernotifikasjonKafkaProducer.sendDone(uuid, mottakerFnr)
        log.info("Har sendt done med uuid $uuid til brukernotifikasjoner")
    }
}
