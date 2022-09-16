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
    val sykepengerMaxDate = LocalDate.now().plusDays(1)

    describe("MaxDateDAOSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }


        it("Store max date") {
            embeddedDatabase.storeSykepengerMaxDate(sykepengerMaxDate, arbeidstakerFnr1, "Infotrygd")
            embeddedDatabase.shouldContainMaxDate(arbeidstakerFnr1, sykepengerMaxDate)
        }

        it("Update max date") {
            embeddedDatabase.storeSykepengerMaxDate(sykepengerMaxDate, arbeidstakerFnr1, "Infotrygd")
            embeddedDatabase.shouldContainMaxDate(arbeidstakerFnr1, sykepengerMaxDate)
            embeddedDatabase.updateMaxDateByFnr(sykepengerMaxDate.plusDays(1), arbeidstakerFnr1, "Spleis")
            embeddedDatabase.shouldContainMaxDate(arbeidstakerFnr1, sykepengerMaxDate.plusDays(1))
        }
    }
})

private fun DatabaseInterface.shouldContainMaxDate(fnr: String, maxDate: LocalDate) =
    this.should("Skal ha rad med forespurt fnr") {
        this.fetchMaxDateByFnr(fnr).equals(maxDate)
    }
