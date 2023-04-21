package no.nav.syfo.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonResponse
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object FysiskBrevUtsendingServiceSpek : Spek({
    val dokarkivService = mockk<DokarkivService>()
    val journalpostdistribusjonConsumer = mockk<JournalpostdistribusjonConsumer>()

    val uuid = "UUID"
    val journalpostId = "journalpostid"
    val bestillingsId = "bestillingsid"

    describe("FysiskBrevUtsendingServiceSpek") {
        coEvery { journalpostdistribusjonConsumer.distribuerJournalpost(journalpostId, uuid) } returns JournalpostdistribusjonResponse(
            bestillingsId
        )

        val fysiskBrevUtsendingService = FysiskBrevUtsendingService(
            journalpostdistribusjonConsumer
        )

        it("Journalpost skal distribueres dersom brev blir sendt til dokarkiv") {
            coEvery { dokarkivService.getJournalpostId(any(), any(), any()) } returns journalpostId
            runBlocking { fysiskBrevUtsendingService.sendBrev(uuid, journalpostId) }
            coVerify(exactly = 1) { journalpostdistribusjonConsumer.distribuerJournalpost(journalpostId, uuid) }
        }
    }
})
