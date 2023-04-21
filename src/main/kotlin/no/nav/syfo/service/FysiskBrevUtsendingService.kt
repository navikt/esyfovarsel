package no.nav.syfo.service

import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import org.slf4j.LoggerFactory

class FysiskBrevUtsendingService(
    val journalpostdistribusjonConsumer: JournalpostdistribusjonConsumer,
) {
    private val log = LoggerFactory.getLogger(FysiskBrevUtsendingService::class.qualifiedName)

    fun sendBrev(
        uuid: String,
        journalpostId: String,
    ) {
        runBlocking {
            val bestillingsId = journalpostdistribusjonConsumer.distribuerJournalpost(journalpostId, uuid).bestillingsId
            log.info("Sendte til print, bestillingsId er $bestillingsId, varsel med UUID: $uuid")
        }
    }
}
