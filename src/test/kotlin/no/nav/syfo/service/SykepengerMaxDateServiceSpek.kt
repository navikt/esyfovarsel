package no.nav.syfo.service

import no.nav.syfo.db.fetchSykepengerMaxDateByFnr
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import kotlin.test.assertEquals

object SykepengerMaxDateServiceSpek : Spek({
    describe("SykepengerMaxDateService") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        val sykepengerMaxDateService = SykepengerMaxDateService(embeddedDatabase)

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Should store new dates") {
            val fiftyDaysFromNow = LocalDate.now().plusDays(50)

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = fiftyDaysFromNow,
                source = SykepengerMaxDateSource.SPLEIS
            )

            val storedMaxDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(fiftyDaysFromNow, storedMaxDate)
        }

        it("Should update existing date") {
            val fiftyDaysFromNow = LocalDate.now().plusDays(50)
            val fourtyDaysFromNow = LocalDate.now().plusDays(40)

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = fiftyDaysFromNow,
                source = SykepengerMaxDateSource.SPLEIS
            )

            val storedMaxDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(fiftyDaysFromNow, storedMaxDate)

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = fourtyDaysFromNow,
                source = SykepengerMaxDateSource.INFOTRYGD
            )

            val newStoredMaxDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(fourtyDaysFromNow, newStoredMaxDate)
        }

        it("Should delete dates older than today") {
            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = LocalDate.now().minusDays(20),
                source = SykepengerMaxDateSource.SPLEIS
            )

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = LocalDate.now().minusDays(19),
                source = SykepengerMaxDateSource.INFOTRYGD
            )

            val storedDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(null, storedDate)
        }

        it("Should delete maxdate if new date is null") {
            val fourtyDaysFromNow = LocalDate.now().plusDays(40)

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = fourtyDaysFromNow,
                source = SykepengerMaxDateSource.SPLEIS
            )

            val storedDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(fourtyDaysFromNow, storedDate)

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = null,
                source = SykepengerMaxDateSource.INFOTRYGD
            )

            val newStoredDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(null, newStoredDate)
        }
    }
})
