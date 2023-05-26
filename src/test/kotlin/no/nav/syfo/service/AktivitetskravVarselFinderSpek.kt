package no.nav.syfo.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.db.arbeidstakerAktorId1
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AktivitetskravVarselFinderSpek : Spek({

    val embeddedDatabase by lazy { EmbeddedDatabase() }
    val pdlConsumerMockk: PdlConsumer = mockk(relaxed = true)
    val aktivitetskravVarselFinder =
        AktivitetskravVarselFinder(embeddedDatabase, pdlConsumerMockk)

    // The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    describe("MerVeiledningVarselFinderSpek") {
        afterEachTest {
            clearAllMocks()
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        val planlagtVarselToStore =
            PlanlagtVarsel(arbeidstakerFnr1, arbeidstakerAktorId1, orgnummer, setOf("1"), VarselType.AKTIVITETSKRAV)

        it("Should send AKTIVITETSKRAV when user is under 70") {
            coEvery { pdlConsumerMockk.isBrukerYngreEnnGittMaxAlder(any(), any()) } returns true

            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)

            val varslerToSendToday = runBlocking {
                aktivitetskravVarselFinder.findAktivitetskravVarslerToSendToday()
            }

            varslerToSendToday.size shouldBeEqualTo 1
        }

        it("Should not send AKTIVITETSKRAV when user is over 70") {
            coEvery { pdlConsumerMockk.isBrukerYngreEnnGittMaxAlder(any(), any()) } returns false
            embeddedDatabase.storePlanlagtVarsel(planlagtVarselToStore)

            val varslerToSendToday = runBlocking {
                aktivitetskravVarselFinder.findAktivitetskravVarslerToSendToday()
            }

            varslerToSendToday.size shouldBeEqualTo 0
        }
    }
})