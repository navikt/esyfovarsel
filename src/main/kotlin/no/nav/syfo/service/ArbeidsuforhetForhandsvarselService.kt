package no.nav.syfo.service

import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.utils.dataToVarselData
import org.slf4j.LoggerFactory

class ArbeidsuforhetForhandsvarselService(private val senderFacade: SenderFacade) {
    
    suspend fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        log.info("[ARBEIDSUFORHET_FORHANDSVARSEL] sending enabled")
        val data = dataToVarselData(varselHendelse.data)
        requireNotNull(data.journalpost)
        requireNotNull(data.journalpost.id)

        log.info("Sending [ARBEIDSUFORHET_FORHANDSVARSEL] to print")
        senderFacade.sendBrevTilFysiskPrint(
            uuid = data.journalpost.uuid,
            varselHendelse = varselHendelse,
            journalpostId = data.journalpost.id,
            distribusjonsType = DistibusjonsType.VIKTIG,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ArbeidsuforhetForhandsvarselService::class.qualifiedName)
    }
}
