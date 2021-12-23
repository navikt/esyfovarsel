package no.nav.syfo.utils

import java.time.LocalDate

class PersonUtil {
    private val BORDER_AGE_YEARS: Long = 70

    fun isNotNotifiableByAge(fnr: String, varslingsdato: LocalDate): Boolean {
        val birthDate = getBirthDateFromFnr(fnr)

        return birthDate.plusYears(BORDER_AGE_YEARS).isEqualOrBefore(varslingsdato)
    }
}
