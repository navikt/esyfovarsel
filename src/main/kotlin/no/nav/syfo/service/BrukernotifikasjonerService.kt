package no.nav.syfo.service

import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.net.URL

class BrukernotifikasjonerService(
    val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
) {
    private val log: Logger = LoggerFactory.getLogger(BrukernotifikasjonerService::class.qualifiedName)

    fun sendVarsel(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL?,
        meldingType: BrukernotifikasjonKafkaProducer.MeldingType?,
        eksternVarsling: Boolean,
        smsContent: String? = null,
    ) {
        when (meldingType) {
            BrukernotifikasjonKafkaProducer.MeldingType.BESKJED -> {
                brukernotifikasjonKafkaProducer.sendBeskjed(mottakerFnr, content, uuid, url, eksternVarsling)
                log.info("Har sendt beskjed med uuid $uuid til brukernotifikasjoner: $content")
            }

            BrukernotifikasjonKafkaProducer.MeldingType.OPPGAVE -> {
                if (url == null) {
                    throw IllegalArgumentException("Url must be set")
                }
                brukernotifikasjonKafkaProducer.sendOppgave(mottakerFnr, content, uuid, url, smsContent)
                log.info("Har sendt oppgave med uuid $uuid til brukernotifikasjoner: $content")
            }

            BrukernotifikasjonKafkaProducer.MeldingType.DONE -> {
                ferdigstillVarsel(uuid, mottakerFnr)
            }

            else -> {
                throw RuntimeException("Ukjent typestreng")
            }
        }
    }

    fun ferdigstillVarsel(
        uuid: String,
        mottakerFnr: String,
    ) {
        brukernotifikasjonKafkaProducer.sendDone(uuid, mottakerFnr)
        log.info("Har sendt done med uuid $uuid til brukernotifikasjoner")
    }
}
