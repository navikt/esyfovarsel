import java.time.*
import no.nav.syfo.utils.*
import org.amshove.kluent.*
import org.spekframework.spek2.*
import org.spekframework.spek2.style.specification.*

object DateUtilSpek : Spek({
    describe("DateUtilSpek") {
        it("Should return true if birthdate is under 67") {
            val birthDate = LocalDate.now().minusYears(30).toString()
            val isBrukerUnder67 = isFodselsdatoMindreEnn67Ar(birthDate)
            isBrukerUnder67 shouldBe true
        }

        it("Should return false if birthdate is over 67") {
            val birthDate = LocalDate.now().minusYears(90).toString()
            val isBrukerUnder67 = isFodselsdatoMindreEnn67Ar(birthDate)
            isBrukerUnder67 shouldBe false
        }

        it("Should return false if user is 67 today") {
            val birthDate = LocalDate.now().minusYears(67).toString()
            val isBrukerUnder67 = isFodselsdatoMindreEnn67Ar(birthDate)
            isBrukerUnder67 shouldBe false
        }

        it("Should return true if user will be 67 next month") {
            val birthDate = LocalDate.now().plusMonths(1).minusYears(67).toString()
            val isBrukerUnder67 = isFodselsdatoMindreEnn67Ar(birthDate)
            isBrukerUnder67 shouldBe true
        }
    }
})
