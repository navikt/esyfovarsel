package no.nav.syfo.service

import io.mockk.*
import no.nav.syfo.db.arbeidstakerFnr1
import no.nav.syfo.db.arbeidstakerFnr2
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.db.orgnummer1
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideMicrofrontendKafkaProducer
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.service.microfrontend.MikrofrontendDialogmoteService
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.testutil.*
import no.nav.syfo.utils.DuplicateMotebehovException
import no.nav.syfo.utils.MotebehovAfterBookingException
import no.nav.syfo.utils.VeilederAlreadyBookedMeetingException
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

object MikrofrontendServiceSpek : Spek({
    val embeddedDatabase by lazy { EmbeddedDatabase() }
    val minSideMicrofrontendKafkaProducer: MinSideMicrofrontendKafkaProducer = mockk(relaxed = true)
    val mikrofrontendDialogmoteService = MikrofrontendDialogmoteService(embeddedDatabase)
    val mikrofrontendService = MikrofrontendService(
        minSideMicrofrontendKafkaProducer,
        mikrofrontendDialogmoteService,
        embeddedDatabase
    )

    defaultTimeout = 20000L

    afterEachTest {
        clearAllMocks()
        embeddedDatabase.connection.dropData()
    }

    afterGroup {
        embeddedDatabase.stop()
    }

    describe("MikrofrontendServiceSpek") {
        justRun { minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any()) }

        val today = LocalDateTime.now()
        val tomorrow = today.plusDays(1L)

        val dataTidspunktToday: String = "{" +
            "\"journalpost\":null," +
            "\"narmesteLeder\":null," +
            "\"motetidspunkt\":{\"tidspunkt\":\"$today\"}" +
            "}"
        val dataTidspunktTomorrow: String = "{" +
            "\"journalpost\":null," +
            "\"narmesteLeder\":null," +
            "\"motetidspunkt\":{\"tidspunkt\":\"$tomorrow\"}" +
            "}"

        val arbeidstakerHendelseDialogmoteInnkalt = ArbeidstakerHendelse(
            type = HendelseType.SM_DIALOGMOTE_INNKALT,
            ferdigstill = false,
            data = dataTidspunktTomorrow,
            arbeidstakerFnr = arbeidstakerFnr1,
            orgnummer = orgnummer1
        )

        val arbeidstakerHendelseDialogmoteNyttTidSted = arbeidstakerHendelseDialogmoteInnkalt.copy(
            type = HendelseType.SM_DIALOGMOTE_NYTT_TID_STED,
            data = dataTidspunktToday
        )

        val arbeidstakerHendelseDialogmoteAvlyst = arbeidstakerHendelseDialogmoteInnkalt.copy(
            type = HendelseType.SM_DIALOGMOTE_AVLYST
        )

        val arbeidstakerHendelseDialogmoteInnkaltIdag = arbeidstakerHendelseDialogmoteInnkalt.copy(
            data = dataTidspunktToday,
            arbeidstakerFnr = arbeidstakerFnr2
        )

        val arbeidstakerHendelseSvarMotebehov = ArbeidstakerHendelse(
            type = HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV,
            ferdigstill = false,
            data = null,
            arbeidstakerFnr = arbeidstakerFnr1,
            orgnummer = orgnummer1
        )

        val arbeidstakerHendelseSvarMotebehovFerdigstill = arbeidstakerHendelseSvarMotebehov.copy(
            ferdigstill = true
        )

        it("Enabling MF with a dialogmote event should result in motetidspunkt storage in DB and publication on min-side topic") {
            mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
            embeddedDatabase.shouldContainMikrofrontendEntry(
                arbeidstakerHendelseDialogmoteInnkalt.arbeidstakerFnr,
                Tjeneste.DIALOGMOTE
            )
            verify(exactly = 1) {
                minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any())
            }
        }

        it("Enabling MF with a syfomotebehov event should result entry without synligTom in DB and publication on min-side topic") {
            mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
            embeddedDatabase.shouldContainMikrofrontendEntryWithoutMotetidspunkt(
                arbeidstakerHendelseDialogmoteInnkalt.arbeidstakerFnr,
                Tjeneste.DIALOGMOTE
            )
            verify(exactly = 1) {
                minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any())
            }
        }

        it("Disabling MF should result in removal of entry from DB and publication on min-side topic") {
            mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
            mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteAvlyst)
            embeddedDatabase.shouldNotContainMikrofrontendEntryForUser(
                arbeidstakerHendelseDialogmoteAvlyst.arbeidstakerFnr
            )
            verify(exactly = 2) {
                minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any())
            }
        }

        it("DM-NyttTidSted event should update 'synligTom' for MF entry") {
            mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
            mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteNyttTidSted)
            val entrySynligTom = embeddedDatabase
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
                arbeidstakerHendelseDialogmoteInnkaltIdag.arbeidstakerFnr
            )
        }

        it("In absence of an DM-innkalling, MF-entry should be deleted upon receiving MB-competion event") {
            mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
            mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehovFerdigstill)
            embeddedDatabase.shouldNotContainMikrofrontendEntryForUser(
                arbeidstakerHendelseDialogmoteInnkaltIdag.arbeidstakerFnr
            )
        }

        it("Receving DM-innkalling event after MB-event, should result in 'synligTom' being set") {
            mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseSvarMotebehov)
            embeddedDatabase.shouldContainMikrofrontendEntryWithoutMotetidspunkt(
                arbeidstakerHendelseSvarMotebehov.arbeidstakerFnr,
                Tjeneste.DIALOGMOTE
            )
            mikrofrontendService.updateMikrofrontendForUserByHendelse(arbeidstakerHendelseDialogmoteInnkalt)
            embeddedDatabase.shouldContainMikrofrontendEntryWithMotetidspunkt(
                arbeidstakerHendelseDialogmoteInnkalt.arbeidstakerFnr,
                Tjeneste.DIALOGMOTE
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
    }
})
