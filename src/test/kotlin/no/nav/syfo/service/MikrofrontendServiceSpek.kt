package no.nav.syfo.service

import io.mockk.*
import no.nav.syfo.db.arbeidstakerFnr1
import no.nav.syfo.db.arbeidstakerFnr2
import no.nav.syfo.db.orgnummer1
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideMicrofrontendKafkaProducer
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.shouldContainMikrofrontendEntry
import no.nav.syfo.testutil.shouldNotContainMikrofrontendEntryForUser
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

object MikrofrontendServiceSpek : Spek({
    val embeddedDatabase by lazy { EmbeddedDatabase() }
    val minSideMicrofrontendKafkaProducer: MinSideMicrofrontendKafkaProducer = mockk(relaxed = true)
    val mikrofrontendService = MikrofrontendService(minSideMicrofrontendKafkaProducer, embeddedDatabase)
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

        val arbeidstakerHendelseDialogmoteAvlyst = arbeidstakerHendelseDialogmoteInnkalt.copy(
            type = HendelseType.SM_DIALOGMOTE_AVLYST
        )

        val arbeidstakerHendelseDialogmoteInnkaltIdag = arbeidstakerHendelseDialogmoteInnkalt.copy(
            data = dataTidspunktToday,
            arbeidstakerFnr = arbeidstakerFnr2
        )

        it("Enabling MF should result in motetidspunkt storage in DB and publication on min-side topic") {
            mikrofrontendService.enableDialogmoteFrontendForUser(arbeidstakerHendelseDialogmoteInnkalt)
            embeddedDatabase.shouldContainMikrofrontendEntry(
                arbeidstakerHendelseDialogmoteInnkalt.arbeidstakerFnr,
                Tjeneste.DIALOGMOTE
            )
            verify(exactly = 1) {
                minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any())
            }
        }

        it("Disabling MF should result in removal of entry from DB and publication on min-side topic") {
            mikrofrontendService.enableDialogmoteFrontendForUser(arbeidstakerHendelseDialogmoteInnkalt)
            mikrofrontendService.disableDialogmoteFrontendForUser(arbeidstakerHendelseDialogmoteAvlyst)
            embeddedDatabase.shouldNotContainMikrofrontendEntryForUser(
                arbeidstakerHendelseDialogmoteAvlyst.arbeidstakerFnr
            )
            verify(exactly = 2) {
                minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(any())
            }
        }

        it("Expired entries should not be persisted") {
            mikrofrontendService.enableDialogmoteFrontendForUser(arbeidstakerHendelseDialogmoteInnkalt)
            mikrofrontendService.enableDialogmoteFrontendForUser(arbeidstakerHendelseDialogmoteInnkaltIdag)
            mikrofrontendService.findAndCloseExpiredDialogmoteMikrofrontends()
            embeddedDatabase.shouldNotContainMikrofrontendEntryForUser(
                arbeidstakerHendelseDialogmoteInnkaltIdag.arbeidstakerFnr
            )
        }
    }
})
