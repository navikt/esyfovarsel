package no.nav.syfo.service

import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.utils.dataToVarselData
import org.slf4j.LoggerFactory

class ManglendeMedvirkningVarselService(
    private val senderFacade: SenderFacade,
) {
    suspend fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        val data = dataToVarselData(varselHendelse.data)
        requireNotNull(data.journalpost)
        requireNotNull(data.journalpost.id)

        log.info("Sending [SM_FORHANDSVARSEL_MANGLENDE_MEDVIRKNING] with uuid ${data.journalpost.uuid} to print")
        senderFacade.sendBrevTilFysiskPrint(
            uuid = data.journalpost.uuid,
            varselHendelse = varselHendelse,
            journalpostId = data.journalpost.id,
            distribusjonsType = DistibusjonsType.VIKTIG,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ManglendeMedvirkningVarselService::class.qualifiedName)
    }
}
