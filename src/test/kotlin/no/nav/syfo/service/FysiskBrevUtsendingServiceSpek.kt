package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonResponse

class FysiskBrevUtsendingServiceSpek : DescribeSpec({
    val dokarkivService = mockk<DokarkivService>()
    val journalpostdistribusjonConsumer = mockk<JournalpostdistribusjonConsumer>()

    val uuid = "UUID"
    val journalpostId = "journalpostid"
    val bestillingsId = "bestillingsid"

    describe("FysiskBrevUtsendingServiceSpek") {
        val fysiskBrevUtsendingService = FysiskBrevUtsendingService(
            journalpostdistribusjonConsumer,
        )

        it("Journalpost skal distribueres dersom brev blir sendt til dokarkiv") {
            coEvery { dokarkivService.getJournalpostId(any(), any(), any()) } returns journalpostId
            coEvery {
                journalpostdistribusjonConsumer.distribuerJournalpost(
                    journalpostId,
                    uuid,
                    DistibusjonsType.ANNET
                )
            } returns JournalpostdistribusjonResponse(
                bestillingsId,
            )
            fysiskBrevUtsendingService.sendBrev(uuid, journalpostId, DistibusjonsType.ANNET)
            coVerify(exactly = 1) {
                journalpostdistribusjonConsumer.distribuerJournalpost(
                    journalpostId,
                    uuid,
                    DistibusjonsType.ANNET
                )
            }
        }
    }
})
