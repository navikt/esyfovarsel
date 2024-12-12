package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.db.arbeidstakerAktorId1
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.db.fetchUtsendtVarselByFnr
import no.nav.syfo.db.fetchUtsendtVarselFeiletByFnr
import no.nav.syfo.db.storeMikrofrontendSynlighetEntry
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.db.storeUtsendtVarselFeilet
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.mineside_microfrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.planner.narmesteLederFnr1
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.shouldBeEqualTo

class TestdataResetServiceSpek : DescribeSpec({
    describe("TestdataResetServiceSpek") {
        val embeddedDatabase = EmbeddedDatabase()
        val mikrofrontendService: MikrofrontendService = mockk(relaxed = true)
        val senderFacade: SenderFacade = mockk(relaxed = true)
        val testdataResetService = TestdataResetService(embeddedDatabase, mikrofrontendService, senderFacade)

        val utsendtVarsel =
            PUtsendtVarsel(
                uuid = UUID.randomUUID().toString(),
                fnr = arbeidstakerFnr1,
                aktorId = arbeidstakerAktorId1,
                narmesteLederFnr = null,
                orgnummer = null,
                type = VarselType.MER_VEILEDNING.name,
                kanal = null,
                utsendtTidspunkt = LocalDateTime.now(),
                planlagtVarselId = null,
                eksternReferanse = null,
                ferdigstiltTidspunkt = null,
                arbeidsgivernotifikasjonMerkelapp = null,
                isForcedLetter = false,
            )

        val pUtsendtVarselFeilet = PUtsendtVarselFeilet(
            UUID.randomUUID().toString(),
            null,
            arbeidstakerFnr1,
            narmesteLederFnr1,
            null,
            HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV.name,
            null,
            null,
            null,
            Kanal.BRUKERNOTIFIKASJON.name,
            null,
            LocalDateTime.now(),
            false
        )

        val mikrofrontendSynlighet =
            MikrofrontendSynlighet(arbeidstakerFnr1, Tjeneste.DIALOGMOTE, LocalDate.now().plusWeeks(1))

        beforeTest {
            embeddedDatabase.dropData()
        }

        it("Reset all testdata") {
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel)
            embeddedDatabase.storeUtsendtVarselFeilet(pUtsendtVarselFeilet)
            embeddedDatabase.storeMikrofrontendSynlighetEntry(mikrofrontendSynlighet)

            //Verify that testdata exists
            embeddedDatabase.fetchUtsendtVarselByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchUtsendtVarselFeiletByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchMikrofrontendSynlighetEntriesByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1

            testdataResetService.resetTestdata(PersonIdent(arbeidstakerFnr1))

            //Check that testdata is deleted
            embeddedDatabase.fetchUtsendtVarselByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            embeddedDatabase.fetchUtsendtVarselFeiletByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            embeddedDatabase.fetchMikrofrontendSynlighetEntriesByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            verify(exactly = 1) {
                mikrofrontendService.closeAllMikrofrontendForUser(PersonIdent(arbeidstakerFnr1))
            }
            coVerify(exactly = 1) {
                senderFacade.ferdigstillVarslerForFnr(PersonIdent(arbeidstakerFnr1))
            }
        }
    }
})
