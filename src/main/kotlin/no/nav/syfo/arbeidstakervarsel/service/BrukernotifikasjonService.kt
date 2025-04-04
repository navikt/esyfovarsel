package no.nav.syfo.arbeidstakervarsel.service

import no.nav.syfo.arbeidstakervarsel.dao.Kanal
import no.nav.syfo.arbeidstakervarsel.service.ArbeidstakervarselService.SendResult
import no.nav.syfo.kafka.consumers.varselbus.domain.BrukernotifikasjonVarsel
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import org.slf4j.LoggerFactory

class BrukernotifikasjonService(private val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer) {
    private val log = LoggerFactory.getLogger(BrukernotifikasjonService::class.java)

    fun sendBrukernotifikasjon(mottakerFnr: String, brukernotifikasjonVarsel: BrukernotifikasjonVarsel): SendResult {
        val uuid = brukernotifikasjonVarsel.uuid

        return try {
            when (brukernotifikasjonVarsel) {
                is BrukernotifikasjonVarsel.Beskjed -> {
                    brukernotifikasjonKafkaProducer.sendBeskjed(
                        fnr = mottakerFnr,
                        content = brukernotifikasjonVarsel.content,
                        uuid = uuid,
                        varselUrl = brukernotifikasjonVarsel.url,
                        eksternVarsling = brukernotifikasjonVarsel.eksternVarsling,
                        dagerTilDeaktivering = brukernotifikasjonVarsel.dagerTilDeaktivering
                    )
                }

                is BrukernotifikasjonVarsel.Oppgave -> {
                    brukernotifikasjonKafkaProducer.sendOppgave(
                        fnr = mottakerFnr,
                        content = brukernotifikasjonVarsel.content,
                        uuid = uuid,
                        varselUrl = brukernotifikasjonVarsel.url,
                        smsContent = brukernotifikasjonVarsel.smsContent,
                        dagerTilDeaktivering = brukernotifikasjonVarsel.dagerTilDeaktivering
                    )
                }

                is BrukernotifikasjonVarsel.Done -> {
                    brukernotifikasjonKafkaProducer.sendDone(
                        uuid = uuid
                    )
                }
            }
            log.info("Successfully sent brukernotifikasjon with uuid: $uuid")
            SendResult(success = true, uuid = uuid, kanal = Kanal.BRUKERNOTIFIKASJON, exception = null)
        } catch (e: Exception) {
            log.error("Failed to send brukernotifikasjon with uuid: $uuid", e)
            SendResult(success = false, uuid = uuid, kanal = Kanal.BRUKERNOTIFIKASJON, exception = e)
        }
    }
}
