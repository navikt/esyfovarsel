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
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.exceptions.DuplicateMotebehovException
import no.nav.syfo.exceptions.MotebehovAfterBookingException
import no.nav.syfo.exceptions.VeilederAlreadyBookedMeetingException
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideMicrofrontendKafkaProducer
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.service.microfrontend.MikrofrontendAktivitetskravService
import no.nav.syfo.service.microfrontend.MikrofrontendDialogmoteService
import no.nav.syfo.service.microfrontend.MikrofrontendMerOppfolgingService
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.shouldContainMikrofrontendEntry
import no.nav.syfo.testutil.shouldContainMikrofrontendEntryWithSynligTom
import no.nav.syfo.testutil.shouldContainMikrofrontendEntryWithoutSynligTom
import no.nav.syfo.testutil.shouldNotContainMikrofrontendEntryForUser
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
                "Enabling MF with a syfomotebehov event should result entry without synligTom in DB and publication on min-side topic",
            ) {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
                embeddedDatabase.shouldContainMikrofrontendEntryWithoutSynligTom(
                    arbeidstakerHendelseDialogmoteInnkalt.arbeidstakerFnr,
                    Tjeneste.DIALOGMOTE,
                )
                verify(exactly = 1) {
                    minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any())
                }
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

            it("In absence of an DM-innkalling, MF-entry should be deleted upon receiving MB-competion event") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehovFerdigstill)
                embeddedDatabase.shouldNotContainMikrofrontendEntryForUser(
                    arbeidstakerHendelseDialogmoteInnkaltIdag.arbeidstakerFnr,
                )
            }

            it("Receving DM-innkalling event after MB-event, should result in 'synligTom' being set") {
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
                embeddedDatabase.shouldContainMikrofrontendEntryWithoutSynligTom(
                    arbeidstakerHendelseSvarMotebehov.arbeidstakerFnr,
                    Tjeneste.DIALOGMOTE,
                )
                mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                embeddedDatabase.shouldContainMikrofrontendEntryWithSynligTom(
                    arbeidstakerHendelseDialogmoteInnkalt.arbeidstakerFnr,
                    Tjeneste.DIALOGMOTE,
                )
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

            it("Duplicate veileder booking events should lead to an exception") {
                assertFailsWith(VeilederAlreadyBookedMeetingException::class) {
                    mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                    mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                }
            }

            it("Motebehov event received after veileder booking event should lead to an exception") {
                assertFailsWith(MotebehovAfterBookingException::class) {
                    mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
                    mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
                }
            }

            it("Two consecutive motebehov events should raise an exception") {
                assertFailsWith(DuplicateMotebehovException::class) {
                    mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
                    mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
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
            }
        }
    })
