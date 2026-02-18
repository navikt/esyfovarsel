package no.nav.syfo.job

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.setUtsendtVarselToFerdigstilt
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.service.SenderFacade
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDateTime
import java.util.UUID

class SendForcedAktivitetspliktLetterJobSpek :
    DescribeSpec({

        describe("SendForcedAktivitetspliktLetterJobSpek") {

            val embeddedDatabase = EmbeddedDatabase()
            val senderFacade = mockk<SenderFacade>(relaxed = true)
            val job = SendAktivitetspliktLetterToSentralPrintJob(embeddedDatabase, senderFacade)

            beforeTest {
                embeddedDatabase.dropData()
            }

            it("Sends 2 forced letters for all unread varsler older than 2 days") {
                // Should send:
                val utsendtVarsel1 =
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = "12121212121",
                        aktorId = null,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = "SM_AKTIVITETSPLIKT",
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                        planlagtVarselId = null,
                        eksternReferanse = "eksternReferanse-1",
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = "111",
                    )

                // Should send:
                val utsendtVarsel2 =
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = "22121212121",
                        aktorId = null,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = "SM_AKTIVITETSPLIKT",
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                        planlagtVarselId = null,
                        eksternReferanse = "eksternReferanse-2",
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = "222",
                    )

                // Should send:
                val utsendtVarsel3 =
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = "22121212121",
                        aktorId = null,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = "SM_AKTIVITETSPLIKT",
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(2),
                        planlagtVarselId = null,
                        eksternReferanse = "eksternReferanse-3",
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = "333",
                    )

                // Should not send: utsendt varsel was a forced letter
                val utsendtVarsel4 =
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = "22121212121",
                        aktorId = null,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = "SM_AKTIVITETSPLIKT",
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                        planlagtVarselId = null,
                        eksternReferanse = "eksternReferanse-4",
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = true,
                        journalpostId = "444",
                    )

                // Should not send: utsendt varsel missing journalpostId
                val utsendtVarsel5 =
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = "55555555555",
                        aktorId = null,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = "SM_AKTIVITETSPLIKT",
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                        planlagtVarselId = null,
                        eksternReferanse = "eksternReferanse-5",
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = null,
                    )

                // Should not send: utsendt varsel was ferdigstilt
                val eksternReferanse6 = "eksternReferanse-6"
                val utsendtVarsel6 =
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = "22121212121",
                        aktorId = null,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = "SM_AKTIVITETSPLIKT",
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                        planlagtVarselId = null,
                        eksternReferanse = eksternReferanse6,
                        ferdigstiltTidspunkt = LocalDateTime.now(),
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = "666",
                    )
                // Should not send: missing eksternReferanse
                val utsendtVarsel7 =
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = "22121212121",
                        aktorId = null,
                        narmesteLederFnr = null,
                        orgnummer = null,
                        type = "SM_AKTIVITETSPLIKT",
                        kanal = "BRUKERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                        planlagtVarselId = null,
                        eksternReferanse = null,
                        ferdigstiltTidspunkt = LocalDateTime.now(),
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = "777",
                    )

                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel1)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel2)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel3)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel4)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel5)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel6)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel7)
                embeddedDatabase.setUtsendtVarselToFerdigstilt(eksternReferanse6)

                val result = runBlocking { job.sendLetterToTvingSentralPrintFromJob() }

                result shouldBeEqualTo 3

                coVerify(exactly = 1) {
                    senderFacade.sendBrevTilTvingSentralPrint(
                        varselHendelse = any<ArbeidstakerHendelse>(),
                        distribusjonsType = any(),
                        journalpostId = "111",
                        eksternReferanse = "eksternReferanse-1",
                    )
                }

                coVerify(exactly = 1) {
                    senderFacade.sendBrevTilTvingSentralPrint(
                        varselHendelse = any<ArbeidstakerHendelse>(),
                        distribusjonsType = any(),
                        journalpostId = "222",
                        eksternReferanse = "eksternReferanse-2",
                    )
                }

                coVerify(exactly = 1) {
                    senderFacade.sendBrevTilTvingSentralPrint(
                        varselHendelse = any<ArbeidstakerHendelse>(),
                        distribusjonsType = any(),
                        journalpostId = "333",
                        eksternReferanse = "eksternReferanse-3",
                    )
                }

                coVerify(exactly = 0) {
                    senderFacade.sendBrevTilTvingSentralPrint(
                        varselHendelse = any<ArbeidstakerHendelse>(),
                        distribusjonsType = any(),
                        journalpostId = "444",
                        eksternReferanse = "eksternReferanse-4",
                    )
                }

                coVerify(exactly = 0) {
                    senderFacade.sendBrevTilTvingSentralPrint(
                        varselHendelse = any<ArbeidstakerHendelse>(),
                        distribusjonsType = any(),
                        journalpostId = any(),
                        eksternReferanse = "eksternReferanse-5",
                    )
                }

                coVerify(exactly = 0) {
                    senderFacade.sendBrevTilTvingSentralPrint(
                        varselHendelse = any<ArbeidstakerHendelse>(),
                        distribusjonsType = any(),
                        journalpostId = "666",
                        eksternReferanse = "eksternReferanse-6",
                    )
                }
                coVerify(exactly = 0) {
                    senderFacade.sendBrevTilTvingSentralPrint(
                        varselHendelse = any<ArbeidstakerHendelse>(),
                        distribusjonsType = any(),
                        journalpostId = "777",
                        eksternReferanse = "eksternReferanse-7",
                    )
                }
            }
        }
    })
