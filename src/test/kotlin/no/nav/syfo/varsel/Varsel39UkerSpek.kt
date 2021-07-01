package no.nav.syfo.varsel


import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.consumer.domain.*
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime

const val arbeidstakerFnr1 = "12345678901"
const val arbeidstakerAktorId1 = "1234567890123"

object Varsel39UkerSpek : Spek({

    describe("Varsel39UkerSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        val syketilfelleConsumer = mockk<SyfosyketilfelleConsumer>()
        val varsel39Uker = Varsel39Uker(embeddedDatabase, syketilfelleConsumer)

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Varsel blir planlagt når sykmeldingen strekker seg over 39 uker") {

            val fom = LocalDate.now().minusWeeks(38).minusDays(6)
            val tom = LocalDate.now().plusDays(1)
            val sykeforlop = Sykeforlop(
                fom,
                listOf(SimpleSykmelding("1", fom, tom))
            )

            coEvery { syketilfelleConsumer.getSykeforlop(any()) } returns listOf(sykeforlop)

            runBlocking {
                varsel39Uker.processOppfolgingstilfelle(oppfolgingstilfellePerson(), arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                lagreteVarsler.skalHa39UkersVarsel()
            }
        }

        it("Varsel blir ikke planlagt når sykmeldingen ikke strekker seg over 39 uker") {

            val fom = LocalDate.now().minusWeeks(38)
            val tom = LocalDate.now().plusDays(6)
            val sykeforlop = Sykeforlop(fom, listOf(SimpleSykmelding("1", fom, tom)))

            coEvery { syketilfelleConsumer.getSykeforlop(any()) } returns listOf(sykeforlop)

            runBlocking {
                varsel39Uker.processOppfolgingstilfelle(oppfolgingstilfellePerson(), arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                lagreteVarsler.skalIkkeHa39UkersVarsel()
            }
        }

        it("Varsel blir ikke planlagt når det er mer enn 26 uker mellom sykeforløp") {

            val fomForNyesteSykeforlop = LocalDate.now().minusWeeks(2)
            val tomForEldsteSykeforlop = fomForNyesteSykeforlop.minusWeeks(26).minusDays(1)
            val fomForEldsteSykeforlop = tomForEldsteSykeforlop.minusWeeks(39)
            val eldsteSykeforlop = Sykeforlop(
                fomForEldsteSykeforlop,
                listOf(SimpleSykmelding("1", fomForEldsteSykeforlop, tomForEldsteSykeforlop))
            )
            val nyesteSykeforlop = Sykeforlop(fomForNyesteSykeforlop, listOf(SimpleSykmelding("1", fomForNyesteSykeforlop, LocalDate.now().plusDays(1))))

            coEvery { syketilfelleConsumer.getSykeforlop(any()) } returns listOf(eldsteSykeforlop, nyesteSykeforlop)

            runBlocking {
                varsel39Uker.processOppfolgingstilfelle(oppfolgingstilfellePerson(), arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                lagreteVarsler.skalIkkeHa39UkersVarsel()
            }
        }

        it("Varsel blir planlagt når det er mindre enn 26 uker mellom sykeforløp") {

            val fomForNyesteSykeforlop = LocalDate.now().minusWeeks(2)
            val tomForEldsteSykeforlop = fomForNyesteSykeforlop.minusWeeks(25).minusDays(6)
            val fomForEldsteSykeforlop = tomForEldsteSykeforlop.minusWeeks(40)
            val eldsteSykeforlop = Sykeforlop(
                fomForEldsteSykeforlop,
                listOf(SimpleSykmelding("1", fomForEldsteSykeforlop, tomForEldsteSykeforlop))
            )
            val nyesteSykeforlop = Sykeforlop(fomForNyesteSykeforlop, listOf(SimpleSykmelding("1", fomForNyesteSykeforlop, LocalDate.now().plusDays(1))))

            coEvery { syketilfelleConsumer.getSykeforlop(any()) } returns listOf(eldsteSykeforlop, nyesteSykeforlop)

            runBlocking {
                varsel39Uker.processOppfolgingstilfelle(oppfolgingstilfellePerson(), arbeidstakerFnr1)
                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                lagreteVarsler.skalHa39UkersVarsel()
            }
        }

        it("Varsel blir planlagt selv om arbeidstaker har vært sykmeldt over 39 uker allerede") {

            val fom = LocalDate.now().minusWeeks(40)
            val tom = LocalDate.now().plusWeeks(1)
            val sykeforlop = Sykeforlop(
                fom,
                listOf(SimpleSykmelding("1", fom, tom))
            )
            coEvery { syketilfelleConsumer.getSykeforlop(any()) } returns listOf(sykeforlop)

            runBlocking {
                varsel39Uker.processOppfolgingstilfelle(oppfolgingstilfellePerson(), arbeidstakerFnr1)
                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                lagreteVarsler.skalHa39UkersVarsel()
                lagreteVarsler.skalHaUtsendingsdatoIDag()
            }
        }

    }
})

private fun oppfolgingstilfellePerson(): OppfolgingstilfellePerson {
    val syketilfelledag1 = Syketilfelledag(LocalDate.now(), null)
    return OppfolgingstilfellePerson(arbeidstakerFnr1, emptyList(), syketilfelledag1, 0, false, LocalDateTime.now())
}

private fun List<PPlanlagtVarsel>.skalHa39UkersVarsel() = this.should("Skal ha 39-ukersvarsel") {
    size == 1 && filter { it.type == VarselType.MER_VEILEDNING.name }.size == 1
}

private fun List<PPlanlagtVarsel>.skalIkkeHa39UkersVarsel() = this.should("Skal ikke ha 39-ukersvarsel") {
    size == 0
}

private fun List<PPlanlagtVarsel>.skalHaUtsendingsdatoIDag() = this.should("Skal ha 39-ukersvarsel") {
    filter { it.utsendingsdato == LocalDate.now() }.size == 1
}
