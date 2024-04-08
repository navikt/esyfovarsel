package no.nav.syfo.service

import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

class BrukernotifikasjonerService(
    val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
) {
    private val log: Logger = LoggerFactory.getLogger(BrukernotifikasjonerService::class.qualifiedName)

    fun sendOppgave(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL?,
        smsContent: String? = null,
    ) {
        if (url != null) {
            brukernotifikasjonKafkaProducer.sendOppgave(mottakerFnr, content, uuid, url, smsContent)
            log.info("Har sendt oppgave med uuid $uuid til brukernotifikasjoner: $content")
        } else throw IllegalArgumentException("Url must be set")
    }

    fun sendBeskjed(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL?,
        eksternVarsling: Boolean,
    ) {
        brukernotifikasjonKafkaProducer.sendBeskjed(mottakerFnr, content, uuid, url, eksternVarsling)
        log.info("Har sendt beskjed med uuid $uuid til brukernotifikasjoner: $content")
    }

    fun ferdigstillVarsel(
        uuid: String,
    ) {
        brukernotifikasjonKafkaProducer.sendDone(uuid)
        log.info("Har sendt done med uuid $uuid til brukernotifikasjoner")
    }
}
