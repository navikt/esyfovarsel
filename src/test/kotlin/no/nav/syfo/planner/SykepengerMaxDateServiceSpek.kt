package no.nav.syfo.planner

import no.nav.syfo.db.fetchMaxDateByFnr
import no.nav.syfo.planner.MerVeiledningSykepengerMaxDatePlanner
import no.nav.syfo.planner.SykepengerMaxDateSource
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import kotlin.test.assertEquals

object SykepengerMaxDateServiceSpek : Spek({
    describe("MerVeiledningSykepengerMaxDatePlannerSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        val merVeiledningSykepengerMaxDatePlanner = MerVeiledningSykepengerMaxDatePlanner(embeddedDatabase)

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Should store new dates") {
            val fiftyDaysFromNow = LocalDate.now().plusDays(50)

            merVeiledningSykepengerMaxDatePlanner.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = fiftyDaysFromNow,
                source = SykepengerMaxDateSource.SPLEIS
            )

            val storedMaxDate = embeddedDatabase.fetchMaxDateByFnr("123");
            assertEquals(fiftyDaysFromNow, storedMaxDate)
        }

        it("Should update existing date") {
            val fiftyDaysFromNow = LocalDate.now().plusDays(50)
            val fourtyDaysFromNow = LocalDate.now().plusDays(40)

            merVeiledningSykepengerMaxDatePlanner.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = fiftyDaysFromNow,
                source = SykepengerMaxDateSource.SPLEIS
            )

            val storedMaxDate = embeddedDatabase.fetchMaxDateByFnr("123");
            assertEquals(fiftyDaysFromNow, storedMaxDate)

            merVeiledningSykepengerMaxDatePlanner.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = fourtyDaysFromNow,
                source = SykepengerMaxDateSource.INFOTRYGD
            )

            val newStoredMaxDate = embeddedDatabase.fetchMaxDateByFnr("123");
            assertEquals(fourtyDaysFromNow, newStoredMaxDate)
        }

        it("Should delete dates older than today") {
            merVeiledningSykepengerMaxDatePlanner.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = LocalDate.now().minusDays(20),
                source = SykepengerMaxDateSource.SPLEIS
            )

            merVeiledningSykepengerMaxDatePlanner.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = LocalDate.now().minusDays(19),
                source = SykepengerMaxDateSource.INFOTRYGD
            )

            val storedDate = embeddedDatabase.fetchMaxDateByFnr("123");
            assertEquals(null, storedDate)
        }
    }
})
