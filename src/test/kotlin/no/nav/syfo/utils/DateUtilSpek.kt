package no.nav.syfo.utils

import io.kotest.core.spec.style.DescribeSpec
import java.time.LocalDate
import org.amshove.kluent.shouldBe

class DateUtilSpek :
    DescribeSpec({
        describe("DateUtilSpek") {
            val maxAlder = 67
            it("Should return true if birthdate is under 67") {
                val birthDate = LocalDate.now().minusYears(30).toString()
                val isBrukerUnder67 = isAlderMindreEnnGittAr(birthDate, maxAlder)
                isBrukerUnder67 shouldBe true
            }

            it("Should return false if birthdate is over 67") {
                val birthDate = LocalDate.now().minusYears(90).toString()
                val isBrukerUnder67 = isAlderMindreEnnGittAr(birthDate, maxAlder)
                isBrukerUnder67 shouldBe false
            }

            it("Should return false if user is 67 today") {
                val birthDate = LocalDate.now().minusYears(67).toString()
                val isBrukerUnder67 = isAlderMindreEnnGittAr(birthDate, maxAlder)
                isBrukerUnder67 shouldBe false
            }

            it("Should return true if user will be 67 next month") {
                val birthDate =
                    LocalDate
                        .now()
                        .plusMonths(1)
                        .minusYears(67)
                        .toString()
                val isBrukerUnder67 = isAlderMindreEnnGittAr(birthDate, maxAlder)
                isBrukerUnder67 shouldBe true
            }
        }
    })
