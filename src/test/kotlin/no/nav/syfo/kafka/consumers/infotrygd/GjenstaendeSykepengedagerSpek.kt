package no.nav.syfo.kafka.consumers.infotrygd

import io.kotest.core.spec.style.DescribeSpec
import java.time.LocalDate
import java.time.Month.DECEMBER
import java.time.Month.NOVEMBER
import java.util.Calendar.MAY
import kotlin.test.assertEquals

class GjenstaendeSykepengedagerSpek : DescribeSpec({
    describe("GjenstaendeSykepengedagerSpek") {

        it("Should calculate only ukedager") {
            val maxDate = LocalDate.of(2022, DECEMBER, 31)
            val ubetTomDate = LocalDate.of(2022, NOVEMBER, 30)
            assertEquals(22, ubetTomDate.gjenstaendeSykepengedager(maxDate))
        }

        it("Should not include weekend days") {
            val maxDate = LocalDate.of(2022, DECEMBER, 31) //lørdag
            val ubetTomDate = LocalDate.of(2022, DECEMBER, 30)
            assertEquals(0, ubetTomDate.gjenstaendeSykepengedager(maxDate))
        }

        it("Should include weekday") {
            val maxDate = LocalDate.of(2022, DECEMBER, 30)
            val ubetTomDate = LocalDate.of(2022, DECEMBER, 29)
            assertEquals(1, ubetTomDate.gjenstaendeSykepengedager(maxDate))
        }

        it("Should not include same day") {
            val maxDate = LocalDate.of(2022, DECEMBER, 31)
            val ubetTomDate = LocalDate.of(2022, DECEMBER, 31)
            assertEquals(0, ubetTomDate.gjenstaendeSykepengedager(maxDate))
        }

        it("Should handle when other date is before this") {
            val maxDate = LocalDate.of(2023, MAY, 15)
            val ubetTomDate = LocalDate.of(2023, MAY, 16)
            assertEquals(0, ubetTomDate.gjenstaendeSykepengedager(maxDate))
        }
    }
})
