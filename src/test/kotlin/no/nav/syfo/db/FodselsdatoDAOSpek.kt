package no.nav.syfo.db

import no.nav.syfo.testutil.*
import org.amshove.kluent.*
import org.spekframework.spek2.*
import org.spekframework.spek2.style.specification.*

object FodselsdatoDAOSpek : Spek({
    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    describe("FodselsdatoDAOSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Should fetch birthdate") {
            val fodselsdato = "1988-01-01"
            embeddedDatabase.storeFodselsdato(arbeidstakerFnr1, fodselsdato)
            val fetchedFodselsdato = embeddedDatabase.fetchFodselsdatoByFnr(arbeidstakerFnr1).first()
            fetchedFodselsdato shouldBeEqualTo fodselsdato
        }
    }
})