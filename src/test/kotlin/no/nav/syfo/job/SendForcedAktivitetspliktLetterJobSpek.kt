package no.nav.syfo.job

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.arbeidstakerAktorId1
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.service.SenderFacade
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.shouldBeEqualTo

class SendForcedAktivitetspliktLetterJobSpek : DescribeSpec({

    describe("SendForcedAktivitetspliktLetterJobSpek") {
        val db = mockk<DatabaseInterface>(relaxed = true)
        val sendeFacade = mockk<SenderFacade>(relaxed = true)
        val job = SendForcedAktivitetspliktLetterJob(db, sendeFacade)
        val embeddedDatabase = EmbeddedDatabase()

        beforeTest {
            embeddedDatabase.dropData()
        }

        it("Sends letter if varsel wasn't read in more than 2 days") {

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

        it("Sends letter if varsel wasn't read in exactly 2 days") {
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

        it("Does not send letter if varsel wasn't read in less than 2 days") {
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
    }
})
