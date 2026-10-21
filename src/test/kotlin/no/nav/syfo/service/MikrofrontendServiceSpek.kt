package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.db.ARBEIDSTAKER_FNR_1
import no.nav.syfo.db.ARBEIDSTAKER_FNR_2
import no.nav.syfo.db.ORGNUMMER_1
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.db.storeMikrofrontendSynlighetEntry
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.minesidemicrofrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.minesidemicrofrontend.MinSideMicrofrontendKafkaProducer
import no.nav.syfo.kafka.producers.minesidemicrofrontend.MinSideRecord
import no.nav.syfo.kafka.producers.minesidemicrofrontend.Tjeneste
import no.nav.syfo.service.microfrontend.MikrofrontendAktivitetskravService
import no.nav.syfo.service.microfrontend.MikrofrontendDialogmoteService
import no.nav.syfo.service.microfrontend.MikrofrontendMerOppfolgingService
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.shouldContainMikrofrontendEntry
import no.nav.syfo.testutil.shouldContainMikrofrontendEntryWithSynligTom
import no.nav.syfo.testutil.shouldContainMikrofrontendEntryWithoutSynligTom
import no.nav.syfo.testutil.shouldNotContainMikrofrontendEntryForUser
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

class MikrofrontendServiceSpek :
    DescribeSpec({
        val embeddedDatabase = EmbeddedDatabase()
        val minSideMicrofrontendKafkaProducer: MinSideMicrofrontendKafkaProducer = mockk(relaxed = true)
        val mikrofrontendDialogmoteService = MikrofrontendDialogmoteService(embeddedDatabase)
        val mikrofrontendAktivitetskravService = MikrofrontendAktivitetskravService(embeddedDatabase)
        val mikrofrontendMerOppfolgingService = MikrofrontendMerOppfolgingService(embeddedDatabase)
        val mikrofrontendService =
            MikrofrontendService(
                minSideMicrofrontendKafkaProducer,
                mikrofrontendDialogmoteService,
                mikrofrontendAktivitetskravService,
                mikrofrontendMerOppfolgingService,
                embeddedDatabase,
            )

        beforeTest {
            clearAllMocks()
            embeddedDatabase.dropData()
        }

        fun verifySent(
            action: String,
            fnr: String,
            microfrontendId: String,
            times: Int = 1,
        ) = verify(exactly = times) {
            minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(MinSideRecord(action, fnr, microfrontendId))
        }

        fun entriesFor(fnr: String) = embeddedDatabase.fetchMikrofrontendSynlighetEntriesByFnr(fnr)

        describe("MikrofrontendServiceSpek") {
            justRun { minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any()) }

            val today = LocalDateTime.now()
            val tomorrow = today.plusDays(1L)

            val dataTidspunktToday: String =
                "{" +
                    "\"journalpost\":null," +
                    "\"narmesteLeder\":null," +
                    "\"motetidspunkt\":{\"tidspunkt\":\"$today\"}" +
                    "}"
            val dataTidspunktTomorrow: String =
                "{" +
                    "\"journalpost\":null," +
                    "\"narmesteLeder\":null," +
                    "\"motetidspunkt\":{\"tidspunkt\":\"$tomorrow\"}" +
                    "}"

            val arbeidstakerHendelseDialogmoteInnkalt =
                ArbeidstakerHendelse(
                    type = HendelseType.SM_DIALOGMOTE_INNKALT,
                    ferdigstill = false,
                    data = dataTidspunktTomorrow,
                    arbeidstakerFnr = ARBEIDSTAKER_FNR_1,
                    orgnummer = ORGNUMMER_1,
                )

            val arbeidstakerHendelseDialogmoteNyttTidSted =
                arbeidstakerHendelseDialogmoteInnkalt.copy(
                    type = HendelseType.SM_DIALOGMOTE_NYTT_TID_STED,
                    data = dataTidspunktToday,
                )

            val arbeidstakerHendelseDialogmoteAvlyst =
                arbeidstakerHendelseDialogmoteInnkalt.copy(
                    type = HendelseType.SM_DIALOGMOTE_AVLYST,
                )

            val arbeidstakerHendelseDialogmoteInnkaltIdag =
                arbeidstakerHendelseDialogmoteInnkalt.copy(
                    data = dataTidspunktToday,
                    arbeidstakerFnr = ARBEIDSTAKER_FNR_2,
                )

            val arbeidstakerHendelseSvarMotebehov =
                ArbeidstakerHendelse(
                    type = HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV,
                    ferdigstill = false,
                    data = null,
                    arbeidstakerFnr = ARBEIDSTAKER_FNR_1,
                    orgnummer = ORGNUMMER_1,
                )

            val arbeidstakerHendelseSvarMotebehovFerdigstill =
                arbeidstakerHendelseSvarMotebehov.copy(
                    ferdigstill = true,
                )

            it(
                "Enabling MF with a dialogmote event should result in motetidspunkt storage in DB and publication on min-side topic",
            ) {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                embeddedDatabase.shouldContainMikrofrontendEntry(
                    arbeidstakerHendelseDialogmoteInnkalt.arbeidstakerFnr,
                    Tjeneste.DIALOGMOTE,
                )
                verify(exactly = 1) {
                    minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any())
                }
            }

            it(
                "Enabling MF with a syfomotebehov event should store MOTEBEHOV entry without synligTom and enable syfo-motebehov",
            ) {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
                embeddedDatabase.shouldContainMikrofrontendEntryWithoutSynligTom(
                    arbeidstakerHendelseSvarMotebehov.arbeidstakerFnr,
                    Tjeneste.MOTEBEHOV,
                )
                verify(exactly = 1) {
                    minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any())
                }
                verifySent(MikrofrontendService.actionEnabled, ARBEIDSTAKER_FNR_1, MOTEBEHOV_ID)
            }

            it("Disabling MF should result in removal of entry from DB and publication on min-side topic") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteAvlyst)
                embeddedDatabase.shouldNotContainMikrofrontendEntryForUser(
                    arbeidstakerHendelseDialogmoteAvlyst.arbeidstakerFnr,
                )
                verify(exactly = 2) {
                    minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any())
                }
            }

            it("DM-NyttTidSted event should update 'synligTom' for MF entry") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteNyttTidSted)
                val entrySynligTom =
                    embeddedDatabase
                        .fetchMikrofrontendSynlighetEntriesByFnr(arbeidstakerHendelseDialogmoteInnkalt.arbeidstakerFnr)
                        .first()
                        .synligTom!!
                assertEquals(entrySynligTom, today.toLocalDate())
            }

            it("Expired entries should not be persisted") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkaltIdag)
                mikrofrontendService.findAndCloseExpiredMikrofrontends()
                embeddedDatabase.shouldNotContainMikrofrontendEntryForUser(
                    arbeidstakerHendelseDialogmoteInnkaltIdag.arbeidstakerFnr,
                )
            }

            it("MB-completion event should delete MOTEBEHOV entry and disable syfo-motebehov") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehovFerdigstill)
                embeddedDatabase.shouldNotContainMikrofrontendEntryForUser(
                    arbeidstakerHendelseSvarMotebehov.arbeidstakerFnr,
                )
                verifySent(MikrofrontendService.actionDisabled, ARBEIDSTAKER_FNR_1, MOTEBEHOV_ID)
            }

            it("DM-innkalling after MB-event should enable syfo-dialog and leave syfo-motebehov to syfomotebehov") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                embeddedDatabase.shouldContainMikrofrontendEntryWithoutSynligTom(ARBEIDSTAKER_FNR_1, Tjeneste.MOTEBEHOV)
                embeddedDatabase.shouldContainMikrofrontendEntryWithSynligTom(ARBEIDSTAKER_FNR_1, Tjeneste.DIALOGMOTE)
                verifySent(MikrofrontendService.actionEnabled, ARBEIDSTAKER_FNR_1, MOTEBEHOV_ID)
                verifySent(MikrofrontendService.actionEnabled, ARBEIDSTAKER_FNR_1, DIALOGMOTE_ID)
                verify(exactly = 0) {
                    minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(
                        match { it.eventType == MikrofrontendService.actionDisabled },
                    )
                }
            }

            it("Should store MER_OPPFOLGING event in DB with synligTom set") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(
                    ArbeidstakerHendelse(
                        type = HendelseType.SM_MER_VEILEDNING,
                        ferdigstill = false,
                        data = null,
                        arbeidstakerFnr = ARBEIDSTAKER_FNR_1,
                        orgnummer = null,
                    ),
                )
                embeddedDatabase.shouldContainMikrofrontendEntryWithSynligTom(
                    ARBEIDSTAKER_FNR_1,
                    Tjeneste.MER_OPPFOLGING,
                )
            }

            it("DM-innkalling should always enable syfo-dialog and replace existing DIALOGMOTE entry") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                mikrofrontendService.updateMikrofrontendForUserByHendelse(
                    arbeidstakerHendelseDialogmoteInnkalt.copy(data = dataTidspunktToday),
                )
                verifySent(MikrofrontendService.actionEnabled, ARBEIDSTAKER_FNR_1, DIALOGMOTE_ID, times = 2)
                val entries = entriesFor(ARBEIDSTAKER_FNR_1)
                assertEquals(1, entries.size)
                assertEquals(today.toLocalDate(), entries.single().synligTom)
            }

            it("Repeated motebehov events should enable syfo-motebehov and keep one MOTEBEHOV entry") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
                verifySent(MikrofrontendService.actionEnabled, ARBEIDSTAKER_FNR_1, MOTEBEHOV_ID, times = 2)
                assertEquals(1, entriesFor(ARBEIDSTAKER_FNR_1).count { it.tjeneste == Tjeneste.MOTEBEHOV.name })
            }

            it("MB-completion after DM-innkalling should not touch syfo-dialog") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehovFerdigstill)
                embeddedDatabase.shouldContainMikrofrontendEntryWithSynligTom(ARBEIDSTAKER_FNR_1, Tjeneste.DIALOGMOTE)
                verifySent(MikrofrontendService.actionDisabled, ARBEIDSTAKER_FNR_1, DIALOGMOTE_ID, times = 0)
                verifySent(MikrofrontendService.actionDisabled, ARBEIDSTAKER_FNR_1, MOTEBEHOV_ID)
            }

            describe("Backfill av møtebehov fra syfo-dialog") {
                fun storeLegacyMotebehovEntry(fnr: String) =
                    embeddedDatabase.storeMikrofrontendSynlighetEntry(MikrofrontendSynlighet(fnr, Tjeneste.DIALOGMOTE, null))

                it("moves motebehov entry, enables syfo-motebehov and disables syfo-dialog for user without dialogmote") {
                    storeLegacyMotebehovEntry(ARBEIDSTAKER_FNR_1)

                    mikrofrontendService.moveMotebehovToOwnMikrofrontend()

                    embeddedDatabase.shouldContainMikrofrontendEntryWithoutSynligTom(ARBEIDSTAKER_FNR_1, Tjeneste.MOTEBEHOV)
                    assertEquals(0, entriesFor(ARBEIDSTAKER_FNR_1).count { it.tjeneste == Tjeneste.DIALOGMOTE.name })
                    verifySent(MikrofrontendService.actionEnabled, ARBEIDSTAKER_FNR_1, MOTEBEHOV_ID)
                    verifySent(MikrofrontendService.actionDisabled, ARBEIDSTAKER_FNR_1, DIALOGMOTE_ID)
                }

                it("keeps syfo-dialog for user with dialogmote entry") {
                    storeLegacyMotebehovEntry(ARBEIDSTAKER_FNR_2)
                    embeddedDatabase.storeMikrofrontendSynlighetEntry(
                        MikrofrontendSynlighet(ARBEIDSTAKER_FNR_2, Tjeneste.DIALOGMOTE, LocalDate.now().plusDays(1L)),
                    )

                    mikrofrontendService.moveMotebehovToOwnMikrofrontend()

                    embeddedDatabase.shouldContainMikrofrontendEntryWithoutSynligTom(ARBEIDSTAKER_FNR_2, Tjeneste.MOTEBEHOV)
                    embeddedDatabase.shouldContainMikrofrontendEntryWithSynligTom(ARBEIDSTAKER_FNR_2, Tjeneste.DIALOGMOTE)
                    verifySent(MikrofrontendService.actionEnabled, ARBEIDSTAKER_FNR_2, MOTEBEHOV_ID)
                    verifySent(MikrofrontendService.actionDisabled, ARBEIDSTAKER_FNR_2, DIALOGMOTE_ID, times = 0)
                }

                it("is idempotent") {
                    storeLegacyMotebehovEntry(ARBEIDSTAKER_FNR_1)

                    mikrofrontendService.moveMotebehovToOwnMikrofrontend()
                    mikrofrontendService.moveMotebehovToOwnMikrofrontend()

                    verify(exactly = 2) {
                        minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any())
                    }
                    assertEquals(1, entriesFor(ARBEIDSTAKER_FNR_1).size)
                }

                it("does not touch dialogmote entries with synligTom") {
                    mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                    clearAllMocks()

                    mikrofrontendService.moveMotebehovToOwnMikrofrontend()

                    embeddedDatabase.shouldContainMikrofrontendEntryWithSynligTom(ARBEIDSTAKER_FNR_1, Tjeneste.DIALOGMOTE)
                    verify(exactly = 0) {
                        minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any())
                    }
                }
            }

            it("Closing entries for user should not close other users entries") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkaltIdag)
                mikrofrontendService.closeAllMikrofrontendForUser(
                    PersonIdent(arbeidstakerHendelseDialogmoteInnkaltIdag.arbeidstakerFnr),
                )
                embeddedDatabase.shouldNotContainMikrofrontendEntryForUser(
                    arbeidstakerHendelseDialogmoteInnkaltIdag.arbeidstakerFnr,
                )
                embeddedDatabase.shouldContainMikrofrontendEntry(
                    arbeidstakerHendelseDialogmoteInnkalt.arbeidstakerFnr,
                    Tjeneste.DIALOGMOTE,
                )
                verifySent(MikrofrontendService.actionDisabled, ARBEIDSTAKER_FNR_2, DIALOGMOTE_ID)
                verifySent(MikrofrontendService.actionDisabled, ARBEIDSTAKER_FNR_2, MOTEBEHOV_ID)
            }
        }
    })

private const val DIALOGMOTE_ID = MikrofrontendDialogmoteService.DIALOGMOTE_MIKROFRONTEND_ID
private const val MOTEBEHOV_ID = MikrofrontendDialogmoteService.MOTEBEHOV_MIKROFRONTEND_ID
