package no.nav.syfo.service

import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.BESKJED
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.DONE
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.OPPGAVE
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

class BrukernotifikasjonerService(
    val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
) {
    private val log: Logger = LoggerFactory.getLogger(BrukernotifikasjonerService::class.qualifiedName)

    fun sendBrukernotifikasjonVarsel(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL?,
        varseltype: SenderFacade.InternalBrukernotifikasjonType,
        eksternVarsling: Boolean,
        smsContent: String? = null,
    ) {
        when (varseltype) {
            BESKJED -> {
                brukernotifikasjonKafkaProducer.sendBeskjed(mottakerFnr, content, uuid, url, eksternVarsling)
                log.info("Har sendt beskjed med uuid $uuid til brukernotifikasjoner: $content")
            }

            OPPGAVE -> {
                url?.let {
                    brukernotifikasjonKafkaProducer.sendOppgave(mottakerFnr, content, uuid, it, smsContent)
                    log.info("Har sendt oppgave med uuid $uuid til brukernotifikasjoner: $content")
                } ?: throw IllegalArgumentException("Url must be set")
            }

            DONE -> {
                ferdigstillVarsel(uuid)
            }
        }
    }

    fun ferdigstillVarsel(
        uuid: String,
    ) {
        brukernotifikasjonKafkaProducer.sendDone(uuid)
        log.info("Har sendt done med uuid $uuid til brukernotifikasjoner")
    }
}
