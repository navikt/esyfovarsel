package no.nav.syfo.job

import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.db.arbeidstakerAktorId1
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.setUtsendtVarselToFerdigstilt
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.service.SenderFacade
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.shouldBeEqualTo

@Ignored //TODO
class SendForcedAktivitetspliktLetterJobSpek : DescribeSpec({

    describe("SendForcedAktivitetspliktLetterJobSpek") {

        val embeddedDatabase = EmbeddedDatabase()
        val senderFacade = mockk<SenderFacade>(relaxed = true)
        val job = SendForcedAktivitetspliktLetterJob(embeddedDatabase, senderFacade)

        beforeTest {
            embeddedDatabase.dropData()
        }

        it("Returns varsel if varsel wasn't read in more than 2 days") {

            val utsendtVarsel =
                PUtsendtVarsel(
                    uuid = UUID.randomUUID().toString(),
                    fnr = arbeidstakerFnr1,
                    aktorId = arbeidstakerAktorId1,
                    narmesteLederFnr = null,
                    orgnummer = null,
                    type = VarselType.MER_VEILEDNING.name,
                    kanal = "BRUKERNOTIFIKASJON",
                    utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                    planlagtVarselId = null,
                    eksternReferanse = null,
                    ferdigstiltTidspunkt = null,
                    arbeidsgivernotifikasjonMerkelapp = null,
                    isForcedLetter = true,
                    journalpostId = "111"
                )

            val isVarselOverdude = job.isVarselUnredIn2Days(utsendtVarsel)

            isVarselOverdude shouldBeEqualTo true
        }

        it("Returns varsel if varsel wasn't read in exactly 2 days") {
            val utsendtVarsel =
                PUtsendtVarsel(
                    uuid = UUID.randomUUID().toString(),
                    fnr = arbeidstakerFnr1,
                    aktorId = arbeidstakerAktorId1,
                    narmesteLederFnr = null,
                    orgnummer = null,
                    type = VarselType.MER_VEILEDNING.name,
                    kanal = "BRUKERNOTIFIKASJON",
                    utsendtTidspunkt = LocalDateTime.now().minusDays(2),
                    planlagtVarselId = null,
                    eksternReferanse = null,
                    ferdigstiltTidspunkt = null,
                    arbeidsgivernotifikasjonMerkelapp = null,
                    isForcedLetter = true,
                    journalpostId = "222",
                )

            val isVarselOverdude = job.isVarselUnredIn2Days(utsendtVarsel)

            isVarselOverdude shouldBeEqualTo true
        }

        it("Does not return varsel if varsel wasn't read in less than 2 days") {
            val utsendtVarsel =
                PUtsendtVarsel(
                    uuid = UUID.randomUUID().toString(),
                    fnr = arbeidstakerFnr1,
                    aktorId = arbeidstakerAktorId1,
                    narmesteLederFnr = null,
                    orgnummer = null,
                    type = VarselType.MER_VEILEDNING.name,
                    kanal = "BRUKERNOTIFIKASJON",
                    utsendtTidspunkt = LocalDateTime.now().minusDays(1),
                    planlagtVarselId = null,
                    eksternReferanse = null,
                    ferdigstiltTidspunkt = null,
                    arbeidsgivernotifikasjonMerkelapp = null,
                    isForcedLetter = true,
                    journalpostId = "333",
                )

            val isVarselOverdude = job.isVarselUnredIn2Days(utsendtVarsel)

            isVarselOverdude shouldBeEqualTo false
        }

        it("Sends 2 forced letters for all unread varsler older than 2 days") {
            // Should send:
            val utsendtVarsel1 = PUtsendtVarsel(
                uuid = UUID.randomUUID().toString(),
                fnr = "12121212121",
                aktorId = null,
                narmesteLederFnr = null,
                orgnummer = null,
                type = "SM_AKTIVITETSPLIKT",
                kanal = "BRUKERNOTIFIKASJON",
                utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                planlagtVarselId = null,
                eksternReferanse = null,
                ferdigstiltTidspunkt = null,
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "111"
            )

            // Should send:
            val utsendtVarsel2 = PUtsendtVarsel(
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
                ferdigstiltTidspunkt = null,
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "222"
            )

            // Should send:
            val utsendtVarsel3 = PUtsendtVarsel(
                uuid = UUID.randomUUID().toString(),
                fnr = "22121212121",
                aktorId = null,
                narmesteLederFnr = null,
                orgnummer = null,
                type = "SM_AKTIVITETSPLIKT",
                kanal = "BRUKERNOTIFIKASJON",
                utsendtTidspunkt = LocalDateTime.now().minusDays(2),
                planlagtVarselId = null,
                eksternReferanse = null,
                ferdigstiltTidspunkt = null,
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "333"
            )

            // Should not send: utsendt varsel was a forced letter
            val utsendtVarsel4 = PUtsendtVarsel(
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
                ferdigstiltTidspunkt = null,
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = true,
                journalpostId = "444"
            )

            // Should not send: utsendt varsel missing journalpostId
            val utsendtVarsel5 = PUtsendtVarsel(
                uuid = UUID.randomUUID().toString(),
                fnr = "55555555555",
                aktorId = null,
                narmesteLederFnr = null,
                orgnummer = null,
                type = "SM_AKTIVITETSPLIKT",
                kanal = "BRUKERNOTIFIKASJON",
                utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                planlagtVarselId = null,
                eksternReferanse = null,
                ferdigstiltTidspunkt = null,
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = null,
            )

            // Should not send: utsendt varsel was ferdigstilt
            val eksternReferanse = "123"
            val utsendtVarsel6 = PUtsendtVarsel(
                uuid = UUID.randomUUID().toString(),
                fnr = "22121212121",
                aktorId = null,
                narmesteLederFnr = null,
                orgnummer = null,
                type = "SM_AKTIVITETSPLIKT",
                kanal = "BRUKERNOTIFIKASJON",
                utsendtTidspunkt = LocalDateTime.now().minusDays(3),
                planlagtVarselId = null,
                eksternReferanse = "123",
                ferdigstiltTidspunkt = LocalDateTime.now(),
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "666"
            )

            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel1)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel2)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel3)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel4)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel5)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel6)
            embeddedDatabase.setUtsendtVarselToFerdigstilt(eksternReferanse)

            val result = runBlocking { job.sendForcedLetterFromJob() }

            result shouldBeEqualTo 3

            coVerify(exactly = 1) {
                senderFacade.sendForcedBrevTilFysiskPrint(
                    uuid = utsendtVarsel1.uuid,
                    varselHendelse = any<ArbeidstakerHendelse>(),
                    distribusjonsType = any(),
                    journalpostId = "111"
                )
            }

            coVerify(exactly = 1) {
                senderFacade.sendForcedBrevTilFysiskPrint(
                    uuid = utsendtVarsel2.uuid,
                    varselHendelse = any<ArbeidstakerHendelse>(),
                    distribusjonsType = any(),
                    journalpostId = "222"
                )
            }

            coVerify(exactly = 1) {
                senderFacade.sendForcedBrevTilFysiskPrint(
                    uuid = utsendtVarsel3.uuid,
                    varselHendelse = any<ArbeidstakerHendelse>(),
                    distribusjonsType = any(),
                    journalpostId = "333"
                )
            }

            coVerify(exactly = 0) {
                senderFacade.sendForcedBrevTilFysiskPrint(
                    uuid = utsendtVarsel4.uuid,
                    varselHendelse = any<ArbeidstakerHendelse>(),
                    distribusjonsType = any(),
                    journalpostId = "444"
                )
            }

            coVerify(exactly = 0) {
                senderFacade.sendForcedBrevTilFysiskPrint(
                    uuid = utsendtVarsel5.uuid,
                    varselHendelse = any<ArbeidstakerHendelse>(),
                    distribusjonsType = any(),
                    journalpostId = any(),
                )
            }

            coVerify(exactly = 0) {
                senderFacade.sendForcedBrevTilFysiskPrint(
                    uuid = utsendtVarsel6.uuid,
                    varselHendelse = any<ArbeidstakerHendelse>(),
                    distribusjonsType = any(),
                    journalpostId = "666"
                )
            }
        }

    }
})
