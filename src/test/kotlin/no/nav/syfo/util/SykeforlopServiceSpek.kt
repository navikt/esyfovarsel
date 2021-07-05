package no.nav.syfo.util

import io.ktor.util.*
import no.nav.syfo.consumer.domain.Syketilfellebit
import no.nav.syfo.consumer.domain.Syketilfelledag
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime

const val SYKEFORLOP_MIN_DIFF_DAGER: Long = 16L
const val arbeidstakerAktorId1 = "1234567890123"


@KtorExperimentalAPI
object AktivitetskravVarselPlannerSpek : Spek({
    val sykeforlopService = SykeforlopService()

    describe("AktivitetskravVarselPlannerSpek") {

        it("Skal lage 1 sykeforlop") {
            val fom1 = LocalDate.now()
            val tom1 = LocalDate.now().plusDays(2)
            val fom2 = LocalDate.now().plusDays(4)
            val tom2 = LocalDate.now().plusDays(6)

            val syketilfellebit1 = opprettSyketilfellebit("1", "3", fom1, tom1)
            val syketilfellebit2 = opprettSyketilfellebit("2", "3", fom1, tom1)
            val syketilfellebit3 = opprettSyketilfellebit("3", "8", fom2, tom2)
            val syketilfellebit4 = opprettSyketilfellebit("4", "8", fom2, tom2)

            val syketilfelledag1 = Syketilfelledag(LocalDate.of(2021, 6, 1), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.of(2021, 6, 2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.of(2021, 6, 4), syketilfellebit3)
            val syketilfelledag4 = Syketilfelledag(LocalDate.of(2021, 6, 5), syketilfellebit4)

            val forlopliste = sykeforlopService.getSykeforlopList(listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3, syketilfelledag4))

            forlopliste.size shouldEqual 1
            forlopliste[0].fom shouldEqual fom1
            forlopliste[0].tom shouldEqual tom2
        }

        it("Skal lage 2 sykeforlop") {
            val fom1 = LocalDate.now()
            val tom1 = LocalDate.now().plusDays(2)
            val fom2 = tom1.plusDays(SYKEFORLOP_MIN_DIFF_DAGER).plusDays(1)
            val tom2 = tom1.plusDays(SYKEFORLOP_MIN_DIFF_DAGER).plusDays(7)

            val syketilfellebit1 = opprettSyketilfellebit("1", "3", fom1, tom1)
            val syketilfellebit2 = opprettSyketilfellebit("2", "3", fom1, tom1)
            val syketilfellebit3 = opprettSyketilfellebit("3", "8", fom2, tom2)
            val syketilfellebit4 = opprettSyketilfellebit("4", "8", fom2, tom2)

            val syketilfelledag1 = Syketilfelledag(LocalDate.now(), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.now(), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.now(), syketilfellebit3)
            val syketilfelledag4 = Syketilfelledag(LocalDate.now(), syketilfellebit4)

            val forlopliste = sykeforlopService.getSykeforlopList(listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3, syketilfelledag4))

            forlopliste.size shouldEqual 2
            forlopliste[0].fom shouldEqual fom1
            forlopliste[1].fom shouldEqual fom2
        }

        it("Skal lage 1 sykeforlop med tom fra siste tilfellebit") {
            val fom1 = LocalDate.now()
            val tom1 = LocalDate.now().plusDays(2)
            val fom2 = LocalDate.now().minusDays(1)
            val tom2 = LocalDate.now().plusDays(1)

            val syketilfellebit1 = opprettSyketilfellebit("1", "3", fom1, tom1)
            val syketilfellebit2 = opprettSyketilfellebit("2", "3", fom1, tom1)

            // Sykmelding med tidligere tom
            val syketilfellebit4 = opprettSyketilfellebit("4", "8", fom2, tom2, LocalDateTime.now().plusDays(3))
            val syketilfellebit5 = opprettSyketilfellebit("5", "8", fom2, tom2, LocalDateTime.now().plusDays(3))

            val syketilfelledag1 = Syketilfelledag(LocalDate.of(2021, 6, 1), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.of(2021, 6, 2), syketilfellebit2)
            val syketilfelledag4 = Syketilfelledag(LocalDate.of(2021, 6, 1), syketilfellebit4)
            val syketilfelledag5 = Syketilfelledag(LocalDate.of(2021, 6, 2), syketilfellebit5)

            val forlopliste = sykeforlopService.getSykeforlopList(listOf(syketilfelledag1, syketilfelledag2, syketilfelledag4, syketilfelledag5))

            forlopliste.size shouldEqual 1
            forlopliste[0].fom shouldEqual fom1
            forlopliste[0].tom shouldEqual tom2
        }
    }
})

fun opprettSyketilfellebit(id: String, ressursId: String, fom: LocalDate, tom: LocalDate, opprettet: LocalDateTime = LocalDateTime.now()): Syketilfellebit {
    return Syketilfellebit(
        id,
        arbeidstakerAktorId1,
        "2",
        opprettet,
        LocalDateTime.now(),
        listOf("[SYKMELDING, ", "SENDT", "PERIODE", "INGEN_AKTIVITET"),
        ressursId,
        fom.atStartOfDay(),
        tom.atStartOfDay()
    )
}

