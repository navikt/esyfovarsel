package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.db.arbeidstakerAktorId1
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.PSyketilfellebit
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchInfotrygdUtbetalingByFnr
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.fetchSyketilfellebiterByFnr
import no.nav.syfo.db.fetchUtsendtVarselByFnr
import no.nav.syfo.db.fetchUtsendtVarselFeiletByFnr
import no.nav.syfo.db.storeInfotrygdUtbetaling
import no.nav.syfo.db.storeMikrofrontendSynlighetEntry
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.db.storeSpleisUtbetaling
import no.nav.syfo.db.storeSyketilfellebit
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.db.storeUtsendtVarselFeilet
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.infotrygd.domain.InfotrygdSource
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.mineside_microfrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.planner.narmesteLederFnr1
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.syketilfelle.domain.Tag
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.shouldBeEqualTo
import java.sql.Date
import java.sql.Timestamp
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
            PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), VarselType.AKTIVITETSKRAV)

        val utsendtVarsel =
            PUtsendtVarsel(
                uuid = UUID.randomUUID().toString(),
                fnr = arbeidstakerFnr1,
                aktorId = arbeidstakerAktorId1,
                utsendtTidspunkt = LocalDateTime.now(),
                type = VarselType.AKTIVITETSKRAV.name,
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

        val pSyketilfellebit = PSyketilfellebit(
            UUID.randomUUID(),
            "1",
            arbeidstakerFnr1,
            null,
            Timestamp.valueOf(LocalDateTime.now()),
            Timestamp.valueOf(LocalDateTime.now()),
            Timestamp.valueOf(LocalDateTime.now()),
            Tag.ANNET_FRAVAR.name,
            "hei",
            Date.valueOf(LocalDate.now().toString()),
            Date.valueOf(LocalDate.now().toString()),
            null
        )

        val mikrofrontendSynlighet = MikrofrontendSynlighet(arbeidstakerFnr1, Tjeneste.DIALOGMOTE, LocalDate.now().plusWeeks(1))

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
            embeddedDatabase.storeInfotrygdUtbetaling(arbeidstakerFnr1, LocalDate.now(), LocalDate.now(), 0, InfotrygdSource.MANUAL)
            embeddedDatabase.storeUtsendtVarselFeilet(pUtsendtVarselFeilet)
            embeddedDatabase.storeSyketilfellebit(pSyketilfellebit)
            embeddedDatabase.storeMikrofrontendSynlighetEntry(mikrofrontendSynlighet)

            //Verify that testdata exists
            embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchUtsendtVarselByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchSpleisUtbetalingByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchInfotrygdUtbetalingByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchUtsendtVarselFeiletByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchSyketilfellebiterByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1
            embeddedDatabase.fetchMikrofrontendSynlighetEntriesByFnr(arbeidstakerFnr1).size shouldBeEqualTo 1

            testdataResetService.resetTestdata(PersonIdent(arbeidstakerFnr1))

            //Check that testdata is deleted
            embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            embeddedDatabase.fetchUtsendtVarselByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            embeddedDatabase.fetchSpleisUtbetalingByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            embeddedDatabase.fetchInfotrygdUtbetalingByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            embeddedDatabase.fetchUtsendtVarselFeiletByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            embeddedDatabase.fetchSyketilfellebiterByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            embeddedDatabase.fetchMikrofrontendSynlighetEntriesByFnr(arbeidstakerFnr1).size shouldBeEqualTo 0
            verify(exactly = 1) {
                mikrofrontendService.closeAllMikrofrontendForUser(PersonIdent(arbeidstakerFnr1))
            }
            verify(exactly = 1) {
                senderFacade.ferdigstillVarslerForFnr(PersonIdent(arbeidstakerFnr1))
            }
        }
    }
})
