package no.nav.syfo.varsel


import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.domain.OppfolgingstilfellePerson
import no.nav.syfo.consumer.domain.Syketilfellebit
import no.nav.syfo.consumer.domain.Syketilfelledag
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime

const val arbeidstakerFnr1 = "12345678901"
const val arbeidstakerFnr2 = "23456789012"
const val arbeidstakerAktorId1 = "1234567890123"
const val arbeidstakerAktorId2 = "2345678901234"

object Varsel39UkerSpek : Spek({

    describe("Varsel39UkerSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        val varsel39Uker = Varsel39Uker(embeddedDatabase)

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Varsel blir planlagt når sykmeldingen strekker seg over 39 uker") {
            val syketilfellebit1 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "SENDT"), "3", LocalDateTime.now(), LocalDateTime.now())
            val syketilfellebit2 =
                Syketilfellebit("1", arbeidstakerAktorId1, "2", LocalDateTime.now(), LocalDateTime.now(), listOf("SYKMELDING", "UTDANNING"), "3", LocalDateTime.now(), LocalDateTime.now())

            val syketilfelledag1 = Syketilfelledag(LocalDate.now().minusWeeks(20), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now().plusWeeks(21), syketilfellebit2)

            runBlocking {
                val oppfolgingstilfellePerson =
                    OppfolgingstilfellePerson(arbeidstakerFnr1, listOf(syketilfelledag1, syketilfelledag2), syketilfelledag1, 0, false, LocalDateTime.now())
                varsel39Uker.processOppfolgingstilfelle(oppfolgingstilfellePerson, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                val nrOfRowsFetchedTotal = lagreteVarsler.size

                nrOfRowsFetchedTotal shouldEqual 1
                lagreteVarsler.filter { it.type == VarselType.MER_VEILEDNING.name }.size shouldEqual 1
            }
        }


    }
})
