package no.nav.syfo.db

import io.kotest.core.spec.style.DescribeSpec
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.shouldBeEqualTo

class FodselsdatoDAOSpek :
    DescribeSpec({
        describe("FodselsdatoDAOSpek") {
            val embeddedDatabase = EmbeddedDatabase()
            beforeTest {
                embeddedDatabase.dropData()
            }

            it("Should fetch birthdate") {
                val fodselsdato = "1988-01-01"
                embeddedDatabase.storeFodselsdato(arbeidstakerFnr1, fodselsdato)
                val fetchedFodselsdato = embeddedDatabase.fetchFodselsdatoByFnr(arbeidstakerFnr1).first()
                fetchedFodselsdato shouldBeEqualTo fodselsdato
            }
        }
    })
