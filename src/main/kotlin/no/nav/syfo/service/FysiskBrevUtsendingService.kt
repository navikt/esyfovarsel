package no.nav.syfo.service

import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import org.slf4j.LoggerFactory

class FysiskBrevUtsendingService(
    val journalpostdistribusjonConsumer: JournalpostdistribusjonConsumer
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.service.FysiskBrevService")

    fun sendBrev(
        uuid: String,
        journalpostId: String
    ) {
        runBlocking {
            val bestillingsId = journalpostdistribusjonConsumer.distribuerJournalpost(journalpostId)?.bestillingsId

            if (bestillingsId == null) {
                log.error("Forsøkte å sende til print, men noe gikk galt, bestillingsId er null, varsel med UUID: $uuid")
                throw RuntimeException("Bestillingsid er null")
            }
            log.info("Sendte til print, bestillingsId er $bestillingsId, MER_VEILEDNING varsel med UUID: $uuid")
        }
    }
}
