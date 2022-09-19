package no.nav.syfo.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

fun parseDate(date: String): LocalDate {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    return LocalDate.parse(date, formatter)
}
