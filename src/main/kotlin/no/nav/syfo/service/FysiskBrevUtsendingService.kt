package no.nav.syfo.service

import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import org.slf4j.LoggerFactory

class FysiskBrevUtsendingService(
    val journalpostdistribusjonConsumer: JournalpostdistribusjonConsumer,
) {
    private val log = LoggerFactory.getLogger(FysiskBrevUtsendingService::class.qualifiedName)

    suspend fun sendBrev(
        uuid: String,
        journalpostId: String,
        distribusjonsType: DistibusjonsType,
    ) {
        val bestillingsId = journalpostdistribusjonConsumer.distribuerJournalpost(
            journalpostId,
            uuid,
            distribusjonsType
        ).bestillingsId
        log.info("Sendte til print, bestillingsId er $bestillingsId, varsel med UUID: $uuid")
    }

    suspend fun sendForcedBrev(
        uuid: String,
        journalpostId: String,
        distribusjonsType: DistibusjonsType,
    ) {
        val bestillingsId = journalpostdistribusjonConsumer.distribuerTvungetJournalpost(
            journalpostId,
            uuid,
            distribusjonsType
        ).bestillingsId
        log.info("Sendte tvunget  brev til print, bestillingsId er $bestillingsId, varsel med UUID: $uuid")
    }
}

