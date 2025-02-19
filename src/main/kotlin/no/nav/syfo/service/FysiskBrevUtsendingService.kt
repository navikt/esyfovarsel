package no.nav.syfo.service

import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import no.nav.syfo.consumer.pdl.PdlClient
import org.slf4j.LoggerFactory

class FysiskBrevUtsendingService(
    val journalpostdistribusjonConsumer: JournalpostdistribusjonConsumer,
    val pdlClient: PdlClient,
) {
    private val log = LoggerFactory.getLogger(FysiskBrevUtsendingService::class.qualifiedName)

    suspend fun sendBrev(
        uuid: String,
        journalpostId: String,
        distribusjonsType: DistibusjonsType,
        tvingSentralPrint: Boolean = false,
        arbeidstakerFnr:String,
    ) {
        if (pdlClient.isPersonAlive(arbeidstakerFnr)) {
            val bestillingsId = journalpostdistribusjonConsumer.distribuerJournalpost(
                journalpostId,
                uuid,
                distribusjonsType,
                tvingSentralPrint = tvingSentralPrint
            ).bestillingsId
            log.info("Sendte til print, bestillingsId er $bestillingsId, varsel med UUID: $uuid")
        } else {
            log.info("Sender ikke til print pga person er død")
        }
    }
}
