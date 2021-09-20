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
