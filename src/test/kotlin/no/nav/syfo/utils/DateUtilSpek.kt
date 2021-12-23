import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import no.nav.syfo.utils.getBirthYear
import org.amshove.kluent.shouldBeEqualTo

object DateUtilSpek: Spek( {

    describe("DateUtilSpec") {

        it("Sould calculate correct year in 20st century when individ number is < 500") {
            val fnr = "01020349910" // individ number = 499

            val year = getBirthYear(fnr)
            year shouldBeEqualTo 1903
        }

        it("Sould calculate year in 21st century when individ number is > 500 and decade is < 40") {
            val fnr = "01020390010" // individ number = 900

            val year = getBirthYear(fnr)
            year shouldBeEqualTo 2003
        }

      it("Sould calculate year in 20st century when individ number is > 500 and decade is > 40") {
            val fnr = "01024190010" // individ number = 900

            val year = getBirthYear(fnr)
            year shouldBeEqualTo 1941
        }

      it("Sould calculate year in 20st century when individ number is = 500 and decade is = 40") {
            val fnr = "01024050010" // individ number = 500

            val year = getBirthYear(fnr)
            year shouldBeEqualTo 1940
        }
    }
})
