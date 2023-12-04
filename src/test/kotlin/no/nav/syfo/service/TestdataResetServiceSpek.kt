package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.db.*
import no.nav.syfo.db.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.infotrygd.domain.InfotrygdSource
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.mineside_microfrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.planner.narmesteLederFnr1
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


class TestdataResetServiceSpek : DescribeSpec({
    describe("TestdataResetServiceSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        val mikrofrontendService: MikrofrontendService = mockk(relaxed = true)
        val senderFacade: SenderFacade = mockk(relaxed = true)
        val testdataResetService = TestdataResetService(embeddedDatabase, mikrofrontendService, senderFacade)
        val planlagtVarsel =
            PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), VarselType.MER_VEILEDNING)

        val utsendtVarsel =
            PUtsendtVarsel(
                uuid = UUID.randomUUID().toString(),
                fnr = arbeidstakerFnr1,
                aktorId = arbeidstakerAktorId1,
                utsendtTidspunkt = LocalDateTime.now(),
                type = VarselType.MER_VEILEDNING.name,
                narmesteLederFnr = null,
                orgnummer = null,
                kanal = null,
                planlagtVarselId = null,
                eksternReferanse = null,
                ferdigstiltTidspunkt = null,
                arbeidsgivernotifikasjonMerkelapp = null,
            )

        val utbetalingUtbetalt = UtbetalingUtbetalt(
            arbeidstakerFnr1,
            orgnummer,
            "utbetaling",
            "hei",
            LocalDate.now(),
            0,
            0,
            0,
            0,
            LocalDate.now(),
            LocalDate.now(),
            "1",
            "1"
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
            LocalDateTime.now()
        )

        val mikrofrontendSynlighet =
            MikrofrontendSynlighet(arbeidstakerFnr1, Tjeneste.DIALOGMOTE, LocalDate.now().plusWeeks(1))

        afterTest {
            embeddedDatabase.connection.dropData()
        }

        afterSpec {
            embeddedDatabase.stop()
        }


        it("Reset all testdata") {
            embeddedDatabase.storePlanlagtVarsel(planlagtVarsel)
            embeddedDatabase.storeUtsendtVarsel(utsendtVarsel)
            embeddedDatabase.storeSpleisUtbetaling(utbetalingUtbetalt)
            embeddedDatabase.storeInfotrygdUtbetaling(
                arbeidstakerFnr1,
                LocalDate.now(),
                LocalDate.now(),
                0,
                InfotrygdSource.MANUAL
            )
            embeddedDatabase.storeUtsendtVarselFeilet(pUtsendtVarselFeilet)
            embeddedDatabase.storeMikrofrontendSynlighetEntry(mikrofrontendSynlighet)

            //Verify that testdata exists
            embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchUtsendtVarselByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchSpleisUtbetalingByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchInfotrygdUtbetalingByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchUtsendtVarselFeiletByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchMikrofrontendSynlighetEntriesByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1

            testdataResetService.resetTestdata(PersonIdent(arbeidstakerFnr1))

            //Check that testdata is deleted
            embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            embeddedDatabase.fetchUtsendtVarselByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            embeddedDatabase.fetchSpleisUtbetalingByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            embeddedDatabase.fetchInfotrygdUtbetalingByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
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
