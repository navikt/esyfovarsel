package no.nav.syfo.service

import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.tms.varsel.action.Varseltype
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
        url: URL? = null,
        varseltype: Varseltype?,
        eksternVarsling: Boolean = true,
        smsContent: String? = null,
        ferdigstill: Boolean = false,
    ) {
        try {
            when {
                varseltype == Varseltype.Beskjed -> sendBeskjed(
                    uuid = uuid,
                    mottakerFnr = mottakerFnr,
                    content = content,
                    url = url,
                    eksternVarsling = eksternVarsling
                )

                varseltype == Varseltype.Oppgave -> sendOppgave(
                    uuid = uuid,
                    mottakerFnr = mottakerFnr,
                    content = content,
                    url = url,
                    smsContent = smsContent
                )

                ferdigstill -> {
                    ferdigstillVarsel(uuid)
                }

                else -> {
                    log.warn("Unknown varseltype/ferdigstill combo for uuid: $uuid")
                }
            }
        } catch (e: Exception) {
            log.warn("Error while sending varsel to BRUKERNOTIFIKASJON: ${e.message}")
        }
    }

    fun sendOppgave(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL?,
        smsContent: String? = null,
    ) {
        url?.let {
            brukernotifikasjonKafkaProducer.sendOppgave(mottakerFnr, content, uuid, url, smsContent)
            log.info("Har sendt oppgave med uuid $uuid til brukernotifikasjoner: $content")
        } ?: throw IllegalArgumentException("Url must be set")
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
