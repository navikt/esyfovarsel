package no.nav.syfo.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonResponse
import no.nav.syfo.testutil.mocks.fnr1
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFailsWith

object FysiskBrevUtsendingServiceSpek : Spek({
    val dokarkivService = mockk<DokarkivService>()
    val journalpostdistribusjonConsumer = mockk<JournalpostdistribusjonConsumer>()

    val fnr = fnr1
    val uuid = "UUID"
    val gyldigJournalpostId = "journalpostid"
    val bestillingsId = "bestillingsid"

    describe("FysiskBrevUtsendingServiceSpek") {
        coEvery { journalpostdistribusjonConsumer.distribuerJournalpost(gyldigJournalpostId) } returns JournalpostdistribusjonResponse(
            bestillingsId
        )

        val fysiskBrevUtsendingService = FysiskBrevUtsendingService(
            dokarkivService,
            journalpostdistribusjonConsumer
        )

        it("Journalpost skal distribueres dersom brev blir sendt til dokarkiv") {
            coEvery { dokarkivService.getJournalpostId(any(), any(), any()) } returns gyldigJournalpostId
            runBlocking { fysiskBrevUtsendingService.sendBrev(fnr, uuid, ByteArray(1)) }
            coVerify(exactly = 1) { dokarkivService.getJournalpostId(fnr, uuid, ByteArray(1)) }
            coVerify(exactly = 1) { journalpostdistribusjonConsumer.distribuerJournalpost(gyldigJournalpostId) }
        }

        it("Journalpost skal IKKE distribueres dersom brev ikke lagres dokarkiv") {
            coEvery { dokarkivService.getJournalpostId(any(), any(), ByteArray(1)) } returns null

            assertFailsWith(RuntimeException::class) {
                runBlocking { fysiskBrevUtsendingService.sendBrev(fnr, uuid, ByteArray(1)) }
            }

            coVerify(exactly = 2) { dokarkivService.getJournalpostId(fnr, uuid, ByteArray(1)) }
            coVerify(exactly = 1) { journalpostdistribusjonConsumer.distribuerJournalpost(any()) }
        }
    }
})
