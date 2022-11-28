package no.nav.syfo.db

import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object UtbetalingInfotrygdDAOSpek : Spek({

    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L
    val sykepengerMaxDate = LocalDate.now().plusDays(1)

    describe("InfotrygdMaxDateDAOSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }


        it("Store utbetaling") {
            embeddedDatabase.storeInfotrygdUtbetaling(arbeidstakerFnr1, sykepengerMaxDate, sykepengerMaxDate, 0, "TEST")
        }

        it("Store duplicate utbetaling") {
            embeddedDatabase.storeInfotrygdUtbetaling(arbeidstakerFnr1, sykepengerMaxDate, sykepengerMaxDate, 0, "TEST")
            embeddedDatabase.storeInfotrygdUtbetaling(arbeidstakerFnr1, sykepengerMaxDate, sykepengerMaxDate, 30, "TEST")
        }

    }
})
