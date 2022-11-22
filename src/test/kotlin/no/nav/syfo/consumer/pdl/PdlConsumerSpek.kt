import io.mockk.mockk
import java.time.LocalDate
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.getTestEnv
import org.amshove.kluent.shouldBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdlConsumerSpek : Spek({
    val testEnv = getTestEnv()
    val azureAdConsumer = mockk<AzureAdTokenConsumer>(relaxed = true)
    val pdlConsumer = PdlConsumer(testEnv.urlEnv, azureAdConsumer)

    describe("PdlConsumerSpek") {
        it("Should return true if birthdate is under 67") {
            val birthDate = LocalDate.now().minusYears(30).toString()
            val isBrukerUnder67 = pdlConsumer.isFodselsdatoMindreEnn67Ar(birthDate)
            isBrukerUnder67 shouldBe true
        }

        it("Should return false if birthdate is over 67") {
            val birthDate = LocalDate.now().minusYears(90).toString()
            val isBrukerUnder67 = pdlConsumer.isFodselsdatoMindreEnn67Ar(birthDate)
            isBrukerUnder67 shouldBe false
        }

        it("Should return false if user is 67 today") {
            val birthDate = LocalDate.now().minusYears(67).toString()
            val isBrukerUnder67 = pdlConsumer.isFodselsdatoMindreEnn67Ar(birthDate)
            isBrukerUnder67 shouldBe false
        }

        it("Should return true if user will be 67 next month") {
            val birthDate = LocalDate.now().plusMonths(1).minusYears(67).toString()
            val isBrukerUnder67 = pdlConsumer.isFodselsdatoMindreEnn67Ar(birthDate)
            isBrukerUnder67 shouldBe true
        }
    }
})
