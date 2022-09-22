package no.nav.syfo.service

import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import org.slf4j.LoggerFactory

class FysiskBrevUtsendingService(
    val dokarkivService: DokarkivService,
    val journalpostdistribusjonConsumer: JournalpostdistribusjonConsumer
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.service.FysiskBrevService")

    fun sendBrev(
        fnr: String,
        uuid: String
    ) {
        runBlocking {
            val journalpostId = dokarkivService.getJournalpostId(fnr, uuid)
            log.info("Forsøkte å sende data til dokarkiv, journalpostId er $journalpostId, MER_VEILEDNING varsel med UUID: $uuid")

            val bestillingsId =
                journalpostId?.let { journalpostdistribusjonConsumer.distribuerJournalpost(it)?.bestillingsId }

            if (bestillingsId == null) {
                log.error("Forsøkte å sende PDF til print, men noe gikk galt, bestillingsId er null, MER_VEILEDNING varsel med UUID: $uuid")
                throw RuntimeException("Bestillingsid er null")
            }
            log.info("Sendte PDF til print, bestillingsId er $bestillingsId, MER_VEILEDNING varsel med UUID: $uuid")
        }
    }
}
