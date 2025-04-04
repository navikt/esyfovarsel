package no.nav.syfo.arbeidstakervarsel.service

import no.nav.syfo.arbeidstakervarsel.dao.ArbeidstakervarselDao
import no.nav.syfo.arbeidstakervarsel.dao.Kanal
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerVarsel
import no.nav.syfo.kafka.consumers.varselbus.domain.BrukernotifikasjonVarsel
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import org.slf4j.LoggerFactory

class ArbeidstakervarselService(
    private val arbeidstakervarselDao: ArbeidstakervarselDao,
    private val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer
) {
    private val log = LoggerFactory.getLogger(ArbeidstakervarselService::class.java)

    fun processVarsel(arbeidstakerVarsel: ArbeidstakerVarsel) {
        arbeidstakervarselDao.storeArbeidstakerVarselHendele(arbeidstakerVarsel)

        if (arbeidstakerVarsel.brukernotifikasjonVarsel != null) {
            sendBrukernotifikasjon(
                mottakerFnr = arbeidstakerVarsel.mottakerFnr,
                brukernotifikasjonVarsel = arbeidstakerVarsel.brukernotifikasjonVarsel
            )
        }
    }

    private fun sendBrukernotifikasjon(mottakerFnr: String, brukernotifikasjonVarsel: BrukernotifikasjonVarsel) {
        val uuid = brukernotifikasjonVarsel.uuid

        try {
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
            arbeidstakervarselDao.storeUtsendtArbeidstakerVarsel(uuid, Kanal.BRUKERNOTIFIKASJON)
            log.info("Successfully sent brukernotifikasjon with uuid: $uuid")
        } catch (e: Exception) {
            arbeidstakervarselDao.storeUtsendArbeidstakerVarselFeilet(uuid, Kanal.BRUKERNOTIFIKASJON)
            log.error("Failed to send brukernotifikasjon with uuid: $uuid", e)
        }
    }
}