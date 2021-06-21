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
            val syketilfellebit1 = Syketilfellebit(
                "1",
                arbeidstakerAktorId1,
                "2",
                LocalDateTime.now(),
                LocalDateTime.now(),
                listOf("[SYKMELDING, ", "SENDT", "PERIODE", "INGEN_AKTIVITET"),
                "3",
                LocalDate.of(2021, 6, 1).atStartOfDay(),
                LocalDate.of(2021, 6, 2).atStartOfDay()
            )
            val syketilfellebit2 = Syketilfellebit(
                "1",
                arbeidstakerAktorId1,
                "2",
                LocalDateTime.now(),
                LocalDateTime.now(),
                listOf("[SYKMELDING, ", "SENDT", "PERIODE", "INGEN_AKTIVITET"),
                "3",
                LocalDate.of(2021, 6, 1).atStartOfDay(),
                LocalDate.of(2021, 6, 2).atStartOfDay()
            )
            val syketilfellebit3 = Syketilfellebit(
                "2",
                arbeidstakerAktorId1,
                "2",
                LocalDateTime.now(),
                LocalDateTime.now(),
                listOf("[SYKMELDING, ", "SENDT", "PERIODE", "INGEN_AKTIVITET"),
                "8",
                LocalDate.of(2021, 6, 4).atStartOfDay(),
                LocalDate.of(2021, 6, 5).atStartOfDay()
            )
            val syketilfellebit4 = Syketilfellebit(
                "2",
                arbeidstakerAktorId1,
                "2",
                LocalDateTime.now(),
                LocalDateTime.now(),
                listOf("[SYKMELDING, ", "SENDT", "PERIODE", "INGEN_AKTIVITET"),
                "8",
                LocalDate.of(2021, 6, 4).atStartOfDay(),
                LocalDate.of(2021, 6, 5).atStartOfDay()
            )

            val syketilfelledag1 = Syketilfelledag(LocalDate.of(2021, 6, 1), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.of(2021, 6, 2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.of(2021, 6, 4), syketilfellebit3)
            val syketilfelledag4 = Syketilfelledag(LocalDate.of(2021, 6, 5), syketilfellebit4)

            val forloper = sykeforlopService.getSykeforloper(listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3, syketilfelledag4))

            forloper.size shouldEqual 1
            forloper[0].fom shouldEqual LocalDate.of(2021, 6, 1)
            forloper[0].tom shouldEqual LocalDate.of(2021, 6, 5)
        }

        it("Skal lage 2 sykeforloper") {
            val syketilfellebit1 = Syketilfellebit(
                "1",
                arbeidstakerAktorId1,
                "2",
                LocalDateTime.now(),
                LocalDateTime.now(),
                listOf("[SYKMELDING, ", "SENDT", "PERIODE", "INGEN_AKTIVITET"),
                "3",
                LocalDate.of(2021, 6, 1).atStartOfDay(),
                LocalDate.of(2021, 6, 2).atStartOfDay()
            )
            val syketilfellebit2 = Syketilfellebit(
                "1",
                arbeidstakerAktorId1,
                "2",
                LocalDateTime.now(),
                LocalDateTime.now(),
                listOf("[SYKMELDING, ", "SENDT", "PERIODE", "INGEN_AKTIVITET"),
                "3",
                LocalDate.of(2021, 6, 1).atStartOfDay(),
                LocalDate.of(2021, 6, 2).atStartOfDay()
            )
            val syketilfellebit3 = Syketilfellebit(
                "2",
                arbeidstakerAktorId1,
                "2",
                LocalDateTime.now(),
                LocalDateTime.now(),
                listOf("[SYKMELDING, ", "SENDT", "PERIODE", "INGEN_AKTIVITET"),
                "8",
                LocalDate.of(2021, 6, 4).plusDays(SYKEFORLOP_MIN_DIFF_DAGER).plusDays(1).atStartOfDay(),
                LocalDate.of(2021, 6, 5).atStartOfDay()
            )
            val syketilfellebit4 = Syketilfellebit(
                "2",
                arbeidstakerAktorId1,
                "2",
                LocalDateTime.now(),
                LocalDateTime.now(),
                listOf("[SYKMELDING, ", "SENDT", "PERIODE", "INGEN_AKTIVITET"),
                "8",
                LocalDate.of(2021, 6, 4).plusDays(SYKEFORLOP_MIN_DIFF_DAGER).plusDays(1).atStartOfDay(),
                LocalDate.of(2021, 6, 5).atStartOfDay()
            )

            val syketilfelledag1 = Syketilfelledag(LocalDate.of(2021, 6, 1), syketilfellebit1)
            val syketilfelledag2 = Syketilfelledag(LocalDate.of(2021, 6, 2), syketilfellebit2)
            val syketilfelledag3 = Syketilfelledag(LocalDate.of(2021, 6, 4).plusDays(SYKEFORLOP_MIN_DIFF_DAGER).plusDays(1), syketilfellebit3)
            val syketilfelledag4 = Syketilfelledag(LocalDate.of(2021, 6, 5).plusDays(SYKEFORLOP_MIN_DIFF_DAGER).plusDays(1), syketilfellebit4)

            val forloper = sykeforlopService.getSykeforloper(listOf(syketilfelledag1, syketilfelledag2, syketilfelledag3, syketilfelledag4))

            forloper.size shouldEqual 2
        }
    }
})

