package no.nav.syfo.job

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.db.domain.toArbeidstakerHendelse
import no.nav.syfo.db.fetchUtsendtBrukernotifikasjonVarselFeilet
import no.nav.syfo.db.fetchUtsendtDokDistVarselFeilet
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.db.storeUtsendtVarselFeilet
import no.nav.syfo.service.DialogmoteInnkallingSykmeldtVarselService
import no.nav.syfo.service.MerVeiledningVarselService
import no.nav.syfo.service.MotebehovVarselService
import no.nav.syfo.service.SenderFacade
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDateTime
import java.util.*

class ResendFailedVarslerJobTest : DescribeSpec({
    val embeddedDatabase = EmbeddedDatabase()
    val motebehovVarselService = mockk<MotebehovVarselService>(relaxed = true)
    val dialogmoteInnkallingSykmeldtVarselService = mockk<DialogmoteInnkallingSykmeldtVarselService>(relaxed = true)
    val merVeiledningVarselService = mockk<MerVeiledningVarselService>(relaxed = true)
    val senderFacade = mockk<SenderFacade>(relaxed = true)

    beforeTest {
        embeddedDatabase.dropData()
        coEvery { motebehovVarselService.resendVarselTilBrukernotifikasjoner(any()) } returns true
        coEvery { motebehovVarselService.resendVarselTilArbeidsgiverNotifikasjon(any()) } returns true
        every {
            dialogmoteInnkallingSykmeldtVarselService.revarsleArbeidstakerViaBrukernotifikasjoner(any())
        } returns true
        coEvery { merVeiledningVarselService.resendDigitaltVarselTilArbeidstaker(any()) } returns true
        coEvery { senderFacade.sendBrevTilFysiskPrint(any(), any(), any(), any(), any()) } returns true
    }

    val job = ResendFailedVarslerJob(
        db = embeddedDatabase,
        motebehovVarselService = motebehovVarselService,
        dialogmoteInnkallingSykmeldtVarselService = dialogmoteInnkallingSykmeldtVarselService,
        merVeiledningVarselService = merVeiledningVarselService,
        senderFacade = senderFacade,
    )

    describe("Resend brukernotifikasjon varsler") {
        it(
            """Resends failed varsler to brukernotifikasjoner for dialogmote, 
                     motebehov and mer veiledning"""
        ) {
            // Should not resend due to legal reasons
            val aktivitetspliktVarselFeilet1 = PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = UUID.randomUUID().toString(),
                arbeidstakerFnr = "12121212121",
                orgnummer = null,
                hendelsetypeNavn = "SM_AKTIVITETSPLIKT",
                kanal = "BRUKERNOTIFIKASJON",
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "111",
                narmesteLederFnr = null,
                brukernotifikasjonerMeldingType = SenderFacade.InternalBrukernotifikasjonType.OPPGAVE.name,
                feilmelding = "noe galt skjedde",
                utsendtForsokTidspunkt = LocalDateTime.now().minusDays(1),
            )

            // Should resend
            val svarMotebehovVarselFeilet1 = PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = UUID.randomUUID().toString(),
                arbeidstakerFnr = "12121212121",
                orgnummer = null,
                hendelsetypeNavn = "SM_DIALOGMOTE_SVAR_MOTEBEHOV",
                kanal = "BRUKERNOTIFIKASJON",
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "111",
                narmesteLederFnr = null,
                brukernotifikasjonerMeldingType = SenderFacade.InternalBrukernotifikasjonType.OPPGAVE.name,
                feilmelding = "noe galt skjedde",
                utsendtForsokTidspunkt = LocalDateTime.now().minusDays(1),
            )

            // Should resend
            val svarMotebehovVarselFeilet2 = PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = UUID.randomUUID().toString(),
                arbeidstakerFnr = "32121212121",
                orgnummer = null,
                hendelsetypeNavn = "SM_DIALOGMOTE_SVAR_MOTEBEHOV",
                kanal = "BRUKERNOTIFIKASJON",
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "112",
                narmesteLederFnr = null,
                brukernotifikasjonerMeldingType = SenderFacade.InternalBrukernotifikasjonType.OPPGAVE.name,
                feilmelding = "noe galt skjedde",
                utsendtForsokTidspunkt = LocalDateTime.now().minusDays(1),
            )

            // Should not resend due to already resendt
            val svarMotebehovVarselFeilet3 = PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = UUID.randomUUID().toString(),
                arbeidstakerFnr = "32121212121",
                orgnummer = null,
                hendelsetypeNavn = "SM_DIALOGMOTE_SVAR_MOTEBEHOV",
                kanal = "BRUKERNOTIFIKASJON",
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "112",
                narmesteLederFnr = null,
                brukernotifikasjonerMeldingType = SenderFacade.InternalBrukernotifikasjonType.OPPGAVE.name,
                feilmelding = "noe galt skjedde",
                utsendtForsokTidspunkt = LocalDateTime.now().minusDays(1),
                isResendt = true
            )

            // Should resend
            val merOppfolgingVarselFeilet = PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = UUID.randomUUID().toString(),
                arbeidstakerFnr = "32121212121",
                orgnummer = null,
                hendelsetypeNavn = "SM_MER_VEILEDNING",
                kanal = "BRUKERNOTIFIKASJON",
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "112",
                narmesteLederFnr = null,
                brukernotifikasjonerMeldingType = SenderFacade.InternalBrukernotifikasjonType.OPPGAVE.name,
                feilmelding = "noe galt skjedde",
                utsendtForsokTidspunkt = LocalDateTime.now().minusDays(1),
            )

            embeddedDatabase.storeUtsendtVarselFeilet(aktivitetspliktVarselFeilet1)
            embeddedDatabase.storeUtsendtVarselFeilet(svarMotebehovVarselFeilet1)
            embeddedDatabase.storeUtsendtVarselFeilet(svarMotebehovVarselFeilet2)
            embeddedDatabase.storeUtsendtVarselFeilet(svarMotebehovVarselFeilet3)
            embeddedDatabase.storeUtsendtVarselFeilet(merOppfolgingVarselFeilet)

            val result = runBlocking { job.resendFailedBrukernotifikasjonVarsler() }

            result shouldBeEqualTo 3

            coVerify(exactly = 1) {
                merVeiledningVarselService.resendDigitaltVarselTilArbeidstaker(
                    any()
                )
            }
            coVerify(exactly = 2) {
                motebehovVarselService.resendVarselTilBrukernotifikasjoner(any())
            }

            val failedVarslerAfterResend = embeddedDatabase.fetchUtsendtBrukernotifikasjonVarselFeilet()
            failedVarslerAfterResend.size shouldBeEqualTo 0
        }
    }

    describe("Resend dokdist varsler") {
        it(
            """Resends failed varsler to dokdist for all kinds of varsler"""
        ) {
            val merOppfolgingVarselFeilet = PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = UUID.randomUUID().toString(),
                arbeidstakerFnr = "32121212121",
                orgnummer = "32143242",
                hendelsetypeNavn = "SM_MER_VEILEDNING",
                kanal = "BREV",
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "112",
                narmesteLederFnr = null,
                brukernotifikasjonerMeldingType = null,
                feilmelding = "noe galt skjedde",
                utsendtForsokTidspunkt = LocalDateTime.now().minusHours(1),
            )

            val dialogmoteVarselFeilet = PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = UUID.randomUUID().toString(),
                arbeidstakerFnr = "32121212121",
                orgnummer = "123123",
                hendelsetypeNavn = "SM_DIALOGMOTE_INNKALT",
                kanal = "BREV",
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = "113",
                narmesteLederFnr = null,
                brukernotifikasjonerMeldingType = null,
                feilmelding = "noe galt skjedde",
                utsendtForsokTidspunkt = LocalDateTime.now().minusHours(2),
            )

            embeddedDatabase.storeUtsendtVarselFeilet(merOppfolgingVarselFeilet)
            embeddedDatabase.storeUtsendtVarselFeilet(dialogmoteVarselFeilet)

            val result = runBlocking { job.resendFailedDokDistVarsler() }

            result shouldBeEqualTo 2

            coVerify(exactly = 1) {
                senderFacade.sendBrevTilFysiskPrint(
                    uuid = merOppfolgingVarselFeilet.uuidEksternReferanse!!,
                    varselHendelse = merOppfolgingVarselFeilet.toArbeidstakerHendelse(),
                    journalpostId = merOppfolgingVarselFeilet.journalpostId!!,
                    distribusjonsType = DistibusjonsType.VIKTIG,
                    storeFailedUtsending = false,
                )
            }
            coVerify(exactly = 1) {
                senderFacade.sendBrevTilFysiskPrint(
                    uuid = dialogmoteVarselFeilet.uuidEksternReferanse!!,
                    varselHendelse = dialogmoteVarselFeilet.toArbeidstakerHendelse(),
                    journalpostId = dialogmoteVarselFeilet.journalpostId!!,
                    distribusjonsType = DistibusjonsType.ANNET,
                    storeFailedUtsending = false,
                )
            }

            val failedVarslerAfterResend = embeddedDatabase.fetchUtsendtDokDistVarselFeilet()
            failedVarslerAfterResend.size shouldBeEqualTo 0
        }
    }

    describe("Resend arbeidsgivernotifikasjoner") {
        it(
            """Resends failed varsler to arbeidsgivernotifikasjoner for NL_DIALOGMOTE_SVAR_MOTEBEHOV,
                when utsendtVarsel to DINE_SYKEMELDTE is not fedigstilt
            """.trimMargin()
        ) {
            val arbeidstakerFnr = "12121212121"
            val narmesteLederFnr = "32121212121"
            val orgnummer = "32143242"

            val dialogmoteVarselFeilet = PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = UUID.randomUUID().toString(),
                arbeidstakerFnr = arbeidstakerFnr,
                orgnummer = orgnummer,
                hendelsetypeNavn = "NL_DIALOGMOTE_SVAR_MOTEBEHOV",
                kanal = "ARBEIDSGIVERNOTIFIKASJON",
                arbeidsgivernotifikasjonMerkelapp = null,
                journalpostId = null,
                narmesteLederFnr = narmesteLederFnr,
                brukernotifikasjonerMeldingType = null,
                feilmelding = "noe galt skjedde",
                utsendtForsokTidspunkt = LocalDateTime.now().minusHours(2),
            )

            val utsendtVarsel = PUtsendtVarsel(
                uuid = UUID.randomUUID().toString(),
                fnr = arbeidstakerFnr,
                aktorId = null,
                narmesteLederFnr = narmesteLederFnr,
                orgnummer = orgnummer,
                type = "NL_DIALOGMOTE_SVAR_MOTEBEHOV",
                kanal = "DINE_SYKMELDTE",
                utsendtTidspunkt = LocalDateTime.now(),
                planlagtVarselId = null,
                eksternReferanse = UUID.randomUUID().toString(),
                ferdigstiltTidspunkt = null,
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
                journalpostId = null
            )

            embeddedDatabase.storeUtsendtVarselFeilet(dialogmoteVarselFeilet)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel)

            val result = runBlocking { job.resendFailedArbeidsgivernotifikasjonVarsler() }

            result shouldBeEqualTo 1
        }
    }
})
