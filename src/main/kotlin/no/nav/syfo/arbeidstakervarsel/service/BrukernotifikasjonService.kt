package no.nav.syfo.arbeidstakervarsel.service

import no.nav.syfo.arbeidstakervarsel.dao.ArbeidstakerKanal
import no.nav.syfo.arbeidstakervarsel.domain.ArbeidstakerVarselSendResult
import no.nav.syfo.arbeidstakervarsel.domain.BrukernotifikasjonVarsel
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import org.slf4j.LoggerFactory

class BrukernotifikasjonService(private val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer) {
    private val log = LoggerFactory.getLogger(BrukernotifikasjonService::class.java)

    fun sendBrukernotifikasjon(
        mottakerFnr: String,
        brukernotifikasjonVarsel: BrukernotifikasjonVarsel
    ): ArbeidstakerVarselSendResult {
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
            ArbeidstakerVarselSendResult(
                success = true,
                uuid = uuid,
                kanal = ArbeidstakerKanal.BRUKERNOTIFIKASJON,
                exception = null
            )
        } catch (e: Exception) {
            log.error("Failed to send brukernotifikasjon with uuid: $uuid", e)
            ArbeidstakerVarselSendResult(
                success = false,
                uuid = uuid,
                kanal = ArbeidstakerKanal.BRUKERNOTIFIKASJON,
                exception = e
            )
        }
    }
}
