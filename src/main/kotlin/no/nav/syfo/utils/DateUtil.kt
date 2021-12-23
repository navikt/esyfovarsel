package no.nav.syfo.utils

import java.time.LocalDate

fun LocalDate.isEqualOrAfter(other: LocalDate): Boolean {
    return this == other || this.isAfter(other)
}

fun LocalDate.isEqualOrBefore(other: LocalDate): Boolean {
    return this == other || this.isBefore(other)
}

fun todayIsBetweenFomAndTom(fom: LocalDate, tom: LocalDate): Boolean {
    val today = LocalDate.now()
    return dateIsInInterval(today, fom, tom)
}

fun dateIsInInterval(date: LocalDate, start: LocalDate, end: LocalDate): Boolean {
    return date.isEqualOrAfter(start) && date.isEqualOrBefore(end)
}

fun getBirthDateFromFnr(fnr: String): LocalDate {
    val day = fnr.substring(0, 2).toInt()
    val month = fnr.substring(2, 4).toInt()
    val year = getBirthYear(fnr)

    return LocalDate.of(year, month, day)
}

fun getBirthYear(fnr: String): Int {
    val year = fnr.substring(4, 6).toInt()
    val individNumber = fnr.substring(6, 9).toInt()

    return when (individNumber) {
        in 500..999 -> if (year < 40) 2000 + year else 1900 + year
        else -> 1900 + year
    }
}
