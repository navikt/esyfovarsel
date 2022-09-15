package no.nav.syfo.db


import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object MaxDateDAOSpek : Spek({

    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L
    val maxDate = LocalDate.now().plusDays(1)

    describe("MaxDateDAOSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }


        it("Store max date") {
            embeddedDatabase.storeMaxDate(maxDate, arbeidstakerFnr1, "Infotrygd")
            embeddedDatabase.shouldContainMaxDate(arbeidstakerFnr1, maxDate)
        }

        it("Update max date") {
            embeddedDatabase.storeMaxDate(maxDate, arbeidstakerFnr1, "Infotrygd")
            embeddedDatabase.shouldContainMaxDate(arbeidstakerFnr1, maxDate)
            embeddedDatabase.updateMaxDateByFnr(maxDate.plusDays(1), arbeidstakerFnr1, "Spleis")
            embeddedDatabase.shouldContainMaxDate(arbeidstakerFnr1, maxDate.plusDays(1))
        }
    }
})

private fun DatabaseInterface.shouldContainMaxDate(fnr: String, maxDate: LocalDate) =
    this.should("Skal ha red med forespurt fnr") {
        this.fetchMaxDateByFnr(fnr).equals(maxDate)
    }
