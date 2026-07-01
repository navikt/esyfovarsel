package no.nav.syfo.job

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
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
import no.nav.syfo.db.fetchUtsendtVarselFeiletByFnr
import no.nav.syfo.db.setUtsendtVarselToFerdigstilt
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.db.storeUtsendtVarselFeilet
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverNotifikasjonTilAltinnRessursHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.service.ArbeidsgiverVarselResendResult
import no.nav.syfo.service.ArbeidsgiverVarselService
import no.nav.syfo.service.DialogmoteInnkallingSykmeldtVarselService
import no.nav.syfo.service.KartleggingssporsmalVarselService
import no.nav.syfo.service.MerVeiledningVarselService
import no.nav.syfo.service.MotebehovVarselService
import no.nav.syfo.service.OppfolgingsplanVarselService
import no.nav.syfo.service.SenderFacade
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDateTime
import java.util.UUID

class ResendFailedVarslerJobTest :
    DescribeSpec({
        val embeddedDatabase = EmbeddedDatabase()
        val motebehovVarselService = mockk<MotebehovVarselService>(relaxed = true)
        val dialogmoteInnkallingSykmeldtVarselService = mockk<DialogmoteInnkallingSykmeldtVarselService>(relaxed = true)
        val merVeiledningVarselService = mockk<MerVeiledningVarselService>(relaxed = true)
        val kartleggingVarselService = mockk<KartleggingssporsmalVarselService>(relaxed = true)
        val senderFacade = mockk<SenderFacade>(relaxed = true)
        val arbeidsgiverVarselService = mockk<ArbeidsgiverVarselService>(relaxed = true)
        val oppfolgingsplanVarselService = mockk<OppfolgingsplanVarselService>(relaxed = true)

        beforeTest {
            embeddedDatabase.dropData()
            clearAllMocks()
            coEvery { motebehovVarselService.resendVarselTilBrukernotifikasjoner(any()) } returns true
            coEvery { motebehovVarselService.resendVarselTilArbeidsgiverNotifikasjon(any()) } returns true
            every {
                dialogmoteInnkallingSykmeldtVarselService.revarsleArbeidstakerViaBrukernotifikasjoner(any())
            } returns true
            coEvery { merVeiledningVarselService.resendDigitaltVarselTilArbeidstaker(any()) } returns true
            coEvery { senderFacade.sendBrevTilFysiskPrint(any(), any(), any(), any(), any()) } returns true
            coEvery { arbeidsgiverVarselService.resendVarselTilArbeidsgiver(any()) } returns ArbeidsgiverVarselResendResult.RESENT
        }

        val job =
            ResendFailedVarslerJob(
                db = embeddedDatabase,
                motebehovVarselService = motebehovVarselService,
                dialogmoteInnkallingSykmeldtVarselService = dialogmoteInnkallingSykmeldtVarselService,
                merVeiledningVarselService = merVeiledningVarselService,
                kartleggingVarselService = kartleggingVarselService,
                senderFacade = senderFacade,
                arbeidsgiverVarselService = arbeidsgiverVarselService,
                oppfolgingsplanVarselService = oppfolgingsplanVarselService,
            )

        describe("Resend brukernotifikasjon varsler") {
            it(
                """Resends failed varsler to brukernotifikasjoner for dialogmote, 
                     motebehov and mer veiledning""",
            ) {
                // Should not resend due to legal reasons
                val aktivitetspliktVarselFeilet1 =
                    PUtsendtVarselFeilet(
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
                val svarMotebehovVarselFeilet1 =
                    PUtsendtVarselFeilet(
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
                val svarMotebehovVarselFeilet2 =
                    PUtsendtVarselFeilet(
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
                val svarMotebehovVarselFeilet3 =
                    PUtsendtVarselFeilet(
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
                        isResendt = true,
                    )

                // Should resend
                val merOppfolgingVarselFeilet =
                    PUtsendtVarselFeilet(
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
                        any(),
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
                """Resends failed varsler to dokdist for all kinds of varsler""",
            ) {
                val merOppfolgingVarselFeilet =
                    PUtsendtVarselFeilet(
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

                val dialogmoteVarselFeilet =
                    PUtsendtVarselFeilet(
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
                        failedUtsendingUUID = UUID.fromString(merOppfolgingVarselFeilet.uuid),
                    )
                }
                coVerify(exactly = 1) {
                    senderFacade.sendBrevTilFysiskPrint(
                        uuid = dialogmoteVarselFeilet.uuidEksternReferanse!!,
                        varselHendelse = dialogmoteVarselFeilet.toArbeidstakerHendelse(),
                        journalpostId = dialogmoteVarselFeilet.journalpostId!!,
                        distribusjonsType = DistibusjonsType.ANNET,
                        failedUtsendingUUID = UUID.fromString(dialogmoteVarselFeilet.uuid),
                    )
                }

                val failedVarslerAfterResend = embeddedDatabase.fetchUtsendtDokDistVarselFeilet()
                failedVarslerAfterResend.size shouldBeEqualTo 0
            }
        }

        describe("Resend arbeidsgivernotifikasjoner") {
            it(
                """Resends multiple failed varsler to arbeidsgivernotifikasjoner for NL_DIALOGMOTE_SVAR_MOTEBEHOV,
                when utsendtVarsel to DINE_SYKEMELDTE is not fedigstilt
                """.trimMargin(),
            ) {
                val arbeidstakerFnr1 = "12121212121"
                val narmesteLederFnr1 = "32121212121"
                val orgnummer1 = "32143242"

                val arbeidstakerFnr2 = "22121212121"
                val narmesteLederFnr2 = "42121212121"
                val orgnummer2 = "42143242"

                val dialogmoteVarselFeilet1 =
                    PUtsendtVarselFeilet(
                        uuid = UUID.randomUUID().toString(),
                        uuidEksternReferanse = UUID.randomUUID().toString(),
                        arbeidstakerFnr = arbeidstakerFnr1,
                        orgnummer = orgnummer1,
                        hendelsetypeNavn = "NL_DIALOGMOTE_SVAR_MOTEBEHOV",
                        kanal = "ARBEIDSGIVERNOTIFIKASJON",
                        arbeidsgivernotifikasjonMerkelapp = null,
                        journalpostId = null,
                        narmesteLederFnr = narmesteLederFnr1,
                        brukernotifikasjonerMeldingType = null,
                        feilmelding = "noe galt skjedde",
                        utsendtForsokTidspunkt = LocalDateTime.now().minusHours(2),
                    )

                val dialogmoteVarselFeilet2 =
                    PUtsendtVarselFeilet(
                        uuid = UUID.randomUUID().toString(),
                        uuidEksternReferanse = UUID.randomUUID().toString(),
                        arbeidstakerFnr = arbeidstakerFnr2,
                        orgnummer = orgnummer2,
                        hendelsetypeNavn = "NL_DIALOGMOTE_SVAR_MOTEBEHOV",
                        kanal = "ARBEIDSGIVERNOTIFIKASJON",
                        arbeidsgivernotifikasjonMerkelapp = null,
                        journalpostId = null,
                        narmesteLederFnr = narmesteLederFnr2,
                        brukernotifikasjonerMeldingType = null,
                        feilmelding = "noe galt skjedde",
                        utsendtForsokTidspunkt = LocalDateTime.now().minusHours(2),
                    )

                val utsendtVarsel1 =
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = arbeidstakerFnr1,
                        aktorId = null,
                        narmesteLederFnr = narmesteLederFnr1,
                        orgnummer = orgnummer1,
                        type = "NL_DIALOGMOTE_SVAR_MOTEBEHOV",
                        kanal = "DINE_SYKMELDTE",
                        utsendtTidspunkt = LocalDateTime.now(),
                        planlagtVarselId = null,
                        eksternReferanse = UUID.randomUUID().toString(),
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = null,
                    )

                val utsendtVarsel2 =
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = arbeidstakerFnr2,
                        aktorId = null,
                        narmesteLederFnr = narmesteLederFnr2,
                        orgnummer = orgnummer2,
                        type = "NL_DIALOGMOTE_SVAR_MOTEBEHOV",
                        kanal = "DINE_SYKMELDTE",
                        utsendtTidspunkt = LocalDateTime.now(),
                        planlagtVarselId = null,
                        eksternReferanse = UUID.randomUUID().toString(),
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = null,
                    )

                embeddedDatabase.storeUtsendtVarselFeilet(dialogmoteVarselFeilet1)
                embeddedDatabase.storeUtsendtVarselFeilet(dialogmoteVarselFeilet2)

                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel1)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel2)

                val result = runBlocking { job.resendFailedArbeidsgivernotifikasjonVarsler() }

                result shouldBeEqualTo 2
            }
            it(
                """Does not resends failed varsler to arbeidsgivernotifikasjoner for NL_DIALOGMOTE_SVAR_MOTEBEHOV,
                when utsendtVarsel to DINE_SYKEMELDTE is fedigstilt
                """.trimMargin(),
            ) {
                val arbeidstakerFnr = "12121212121"
                val narmesteLederFnr = "32121212121"
                val orgnummer = "32143242"

                val dialogmoteVarselFeilet =
                    PUtsendtVarselFeilet(
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

                val utsendtVarsel =
                    PUtsendtVarsel(
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
                        journalpostId = null,
                    )

                embeddedDatabase.storeUtsendtVarselFeilet(dialogmoteVarselFeilet)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel)
                embeddedDatabase.setUtsendtVarselToFerdigstilt(utsendtVarsel.eksternReferanse!!)

                val result = runBlocking { job.resendFailedArbeidsgivernotifikasjonVarsler() }

                result shouldBeEqualTo 0
            }
            it(
                """Does not resends failed varsler to arbeidsgivernotifikasjoner for NL_DIALOGMOTE_NYTT_TID_STED,
                since it is currently not supported for resending.
                """.trimMargin(),
            ) {
                val arbeidstakerFnr = "12121212121"
                val narmesteLederFnr = "32121212121"
                val orgnummer = "32143242"

                val dialogmoteVarselFeilet =
                    PUtsendtVarselFeilet(
                        uuid = UUID.randomUUID().toString(),
                        uuidEksternReferanse = UUID.randomUUID().toString(),
                        arbeidstakerFnr = arbeidstakerFnr,
                        orgnummer = orgnummer,
                        hendelsetypeNavn = "NL_DIALOGMOTE_NYTT_TID_STED",
                        kanal = "ARBEIDSGIVERNOTIFIKASJON",
                        arbeidsgivernotifikasjonMerkelapp = null,
                        journalpostId = null,
                        narmesteLederFnr = narmesteLederFnr,
                        brukernotifikasjonerMeldingType = null,
                        feilmelding = "noe galt skjedde",
                        utsendtForsokTidspunkt = LocalDateTime.now().minusHours(2),
                    )

                val utsendtVarsel =
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = arbeidstakerFnr,
                        aktorId = null,
                        narmesteLederFnr = narmesteLederFnr,
                        orgnummer = orgnummer,
                        type = "NL_DIALOGMOTE_NYTT_TID_STED",
                        kanal = "DINE_SYKMELDTE",
                        utsendtTidspunkt = LocalDateTime.now(),
                        planlagtVarselId = null,
                        eksternReferanse = UUID.randomUUID().toString(),
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = null,
                        isForcedLetter = false,
                        journalpostId = null,
                    )

                embeddedDatabase.storeUtsendtVarselFeilet(dialogmoteVarselFeilet)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel)

                val result = runBlocking { job.resendFailedArbeidsgivernotifikasjonVarsler() }

                result shouldBeEqualTo 0
            }

            it("Resends failed AG_VARSEL_ALTINN_RESSURS and clears hendelseJson after success") {
                val failedVarsel = failedArbeidsgiverAltinnVarsel()

                embeddedDatabase.storeUtsendtVarselFeilet(failedVarsel)

                val result = runBlocking { job.resendFailedArbeidsgivernotifikasjonVarsler() }

                result shouldBeEqualTo 1
                val storedFailedVarsel =
                    embeddedDatabase
                        .fetchUtsendtVarselFeiletByFnr(failedVarsel.arbeidstakerFnr)
                        .first { it.uuid == failedVarsel.uuid }
                storedFailedVarsel.isResendt shouldBeEqualTo true
                storedFailedVarsel.hendelseJson shouldBeEqualTo null
                coVerify(exactly = 1) { arbeidsgiverVarselService.resendVarselTilArbeidsgiver(any()) }
            }

            it("Does not pick failed AG_VARSEL_ALTINN_RESSURS for resend when varsel is already stored as sent") {
                val failedVarsel = failedArbeidsgiverAltinnVarsel()
                val utsendtVarsel =
                    PUtsendtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = failedVarsel.arbeidstakerFnr,
                        aktorId = null,
                        narmesteLederFnr = null,
                        orgnummer = failedVarsel.orgnummer,
                        type = failedVarsel.hendelsetypeNavn,
                        kanal = "ARBEIDSGIVERNOTIFIKASJON",
                        utsendtTidspunkt = LocalDateTime.now(),
                        planlagtVarselId = null,
                        eksternReferanse = failedVarsel.uuidEksternReferanse,
                        ferdigstiltTidspunkt = null,
                        arbeidsgivernotifikasjonMerkelapp = failedVarsel.arbeidsgivernotifikasjonMerkelapp,
                        isForcedLetter = false,
                        journalpostId = null,
                    )

                embeddedDatabase.storeUtsendtVarselFeilet(failedVarsel)
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel)

                val result = runBlocking { job.resendFailedArbeidsgivernotifikasjonVarsler() }

                result shouldBeEqualTo 0
                val storedFailedVarsel =
                    embeddedDatabase
                        .fetchUtsendtVarselFeiletByFnr(failedVarsel.arbeidstakerFnr)
                        .first { it.uuid == failedVarsel.uuid }
                storedFailedVarsel.isResendt shouldBeEqualTo false
                storedFailedVarsel.hendelseJson shouldBeEqualTo failedVarsel.hendelseJson
                coVerify(exactly = 0) { arbeidsgiverVarselService.resendVarselTilArbeidsgiver(any()) }
            }

            it("Keeps hendelseJson when resend of AG_VARSEL_ALTINN_RESSURS still fails") {
                val failedVarsel = failedArbeidsgiverAltinnVarsel()
                coEvery {
                    arbeidsgiverVarselService.resendVarselTilArbeidsgiver(any())
                } returns ArbeidsgiverVarselResendResult.RETRYABLE_FAILURE

                embeddedDatabase.storeUtsendtVarselFeilet(failedVarsel)

                val result = runBlocking { job.resendFailedArbeidsgivernotifikasjonVarsler() }

                result shouldBeEqualTo 0
                val storedFailedVarsel =
                    embeddedDatabase
                        .fetchUtsendtVarselFeiletByFnr(failedVarsel.arbeidstakerFnr)
                        .first { it.uuid == failedVarsel.uuid }
                storedFailedVarsel.isResendt shouldBeEqualTo false
                storedFailedVarsel.hendelseJson shouldBeEqualTo failedVarsel.hendelseJson
            }

            it("Marks AG_VARSEL_ALTINN_RESSURS as resend exhausted when service returns permanent failure") {
                val failedVarsel = failedArbeidsgiverAltinnVarsel()
                coEvery {
                    arbeidsgiverVarselService.resendVarselTilArbeidsgiver(any())
                } returns ArbeidsgiverVarselResendResult.PERMANENT_FAILURE

                embeddedDatabase.storeUtsendtVarselFeilet(failedVarsel)

                val result = runBlocking { job.resendFailedArbeidsgivernotifikasjonVarsler() }

                result shouldBeEqualTo 0
                val storedFailedVarsel =
                    embeddedDatabase
                        .fetchUtsendtVarselFeiletByFnr(failedVarsel.arbeidstakerFnr)
                        .first { it.uuid == failedVarsel.uuid }
                storedFailedVarsel.resendExhausted shouldBeEqualTo true
                storedFailedVarsel.isResendt shouldBeEqualTo false
                storedFailedVarsel.hendelseJson shouldBeEqualTo failedVarsel.hendelseJson
            }

            it("Marks AG_VARSEL_ALTINN_RESSURS as resend exhausted when uuidEksternReferanse mangler") {
                val failedVarsel = failedArbeidsgiverAltinnVarsel().copy(uuidEksternReferanse = null)

                embeddedDatabase.storeUtsendtVarselFeilet(failedVarsel)

                val result = runBlocking { job.resendFailedArbeidsgivernotifikasjonVarsler() }

                result shouldBeEqualTo 0
                val storedFailedVarsel =
                    embeddedDatabase
                        .fetchUtsendtVarselFeiletByFnr(failedVarsel.arbeidstakerFnr)
                        .first { it.uuid == failedVarsel.uuid }
                storedFailedVarsel.resendExhausted shouldBeEqualTo true
                storedFailedVarsel.isResendt shouldBeEqualTo false
                storedFailedVarsel.hendelseJson shouldBeEqualTo failedVarsel.hendelseJson
                coVerify(exactly = 0) { arbeidsgiverVarselService.resendVarselTilArbeidsgiver(any()) }
            }

            it("Resends failed NL_OPPFOLGINGSPLAN_VARSELBESTILLING and clears hendelseJson after success") {
                val failedVarsel =
                    failedArbeidsgiverNotifikasjonForNarmesteLeder(
                        hendelsetypeNavn = HendelseType.NL_OPPFOLGINGSPLAN_VARSELBESTILLING.name,
                    )
                coEvery {
                    oppfolgingsplanVarselService.resendVarselbestillingTilArbeidsgiverNotifikasjon(failedVarsel)
                } returns ArbeidsgiverVarselResendResult.RESENT

                embeddedDatabase.storeUtsendtVarselFeilet(failedVarsel)

                val result = runBlocking { job.resendFailedArbeidsgivernotifikasjonVarsler() }

                result shouldBeEqualTo 1
                val storedFailedVarsel =
                    embeddedDatabase
                        .fetchUtsendtVarselFeiletByFnr(failedVarsel.arbeidstakerFnr)
                        .first { it.uuid == failedVarsel.uuid }
                storedFailedVarsel.isResendt shouldBeEqualTo true
                storedFailedVarsel.hendelseJson shouldBeEqualTo null
                coVerify(exactly = 1) {
                    oppfolgingsplanVarselService.resendVarselbestillingTilArbeidsgiverNotifikasjon(any())
                }
            }

            it("Keeps NL_OPPFOLGINGSPLAN_VARSELBESTILLING retryable when resend still mangler NL-info") {
                val failedVarsel =
                    failedArbeidsgiverNotifikasjonForNarmesteLeder(
                        hendelsetypeNavn = HendelseType.NL_OPPFOLGINGSPLAN_VARSELBESTILLING.name,
                    )
                coEvery {
                    oppfolgingsplanVarselService.resendVarselbestillingTilArbeidsgiverNotifikasjon(any())
                } returns ArbeidsgiverVarselResendResult.RETRYABLE_FAILURE

                embeddedDatabase.storeUtsendtVarselFeilet(failedVarsel)

                val result = runBlocking { job.resendFailedArbeidsgivernotifikasjonVarsler() }

                result shouldBeEqualTo 0
                val storedFailedVarsel =
                    embeddedDatabase
                        .fetchUtsendtVarselFeiletByFnr(failedVarsel.arbeidstakerFnr)
                        .first { it.uuid == failedVarsel.uuid }
                storedFailedVarsel.isResendt shouldBeEqualTo false
                storedFailedVarsel.resendExhausted shouldBeEqualTo false
                storedFailedVarsel.hendelseJson shouldBeEqualTo failedVarsel.hendelseJson
            }
        }
    })

private fun failedArbeidsgiverAltinnVarsel(
    eksternReferanseId: String = UUID.randomUUID().toString(),
    hendelseJson: String? = null,
): PUtsendtVarselFeilet {
    val hendelse =
        ArbeidsgiverNotifikasjonTilAltinnRessursHendelse(
            type = HendelseType.AG_VARSEL_ALTINN_RESSURS,
            ferdigstill = false,
            data =
                createObjectMapper().readTree(
                    """
                    {
                      "notifikasjonInnhold": {
                        "epostTittel": "Dialogmøte",
                        "epostBody": "Du har mottatt et nytt dialogmøtevarsel",
                        "smsTekst": "Du har mottatt et nytt dialogmøtevarsel",
                        "varselTekst": "Du har fått en ny notifikasjon om dialogmøte i Altinn."
                      }
                    }
                    """.trimIndent(),
                ),
            arbeidstakerFnr = "12121212121",
            eksternReferanseId = eksternReferanseId,
            kilde = "dokumentporten.dialogmote",
            orgnummer = "999888777",
            ressursId = "nav_syfo_dialogmote",
            ressursUrl = "https://www.altinn.no",
        )
    return PUtsendtVarselFeilet(
        uuid = UUID.randomUUID().toString(),
        uuidEksternReferanse = hendelse.eksternReferanseId,
        arbeidstakerFnr = hendelse.arbeidstakerFnr,
        narmesteLederFnr = null,
        orgnummer = hendelse.orgnummer,
        hendelsetypeNavn = hendelse.type.name,
        arbeidsgivernotifikasjonMerkelapp = "Dialogmøte",
        brukernotifikasjonerMeldingType = null,
        journalpostId = null,
        kanal = "ARBEIDSGIVERNOTIFIKASJON",
        feilmelding = "noe gikk galt",
        utsendtForsokTidspunkt = LocalDateTime.now().minusHours(2),
        hendelseJson = hendelseJson ?: createObjectMapper().writeValueAsString(hendelse),
    )
}

private fun failedArbeidsgiverNotifikasjonForNarmesteLeder(
    hendelsetypeNavn: String,
    eksternReferanseId: String = UUID.randomUUID().toString(),
    hendelseJson: String =
        """
        {
          "@type": "NarmesteLederHendelse",
          "type": "$hendelsetypeNavn",
          "ferdigstill": false,
          "narmesteLederFnr": "32121212121",
          "arbeidstakerFnr": "12121212121",
          "orgnummer": "999888777",
          "data": {
            "varselType": "BESKJED",
            "notifikasjonInnhold": {
              "epostTittel": "Tittel",
              "epostBody": "<p>Body</p>",
              "varselTekst": "Varsel"
            }
          }
        }
        """.trimIndent(),
) = PUtsendtVarselFeilet(
    uuid = UUID.randomUUID().toString(),
    uuidEksternReferanse = eksternReferanseId,
    arbeidstakerFnr = "12121212121",
    narmesteLederFnr = "32121212121",
    orgnummer = "999888777",
    hendelsetypeNavn = hendelsetypeNavn,
    arbeidsgivernotifikasjonMerkelapp = "Oppfølging",
    brukernotifikasjonerMeldingType = null,
    journalpostId = null,
    kanal = "ARBEIDSGIVERNOTIFIKASJON",
    feilmelding = "noe gikk galt",
    utsendtForsokTidspunkt = LocalDateTime.now().minusHours(2),
    hendelseJson = hendelseJson,
)
