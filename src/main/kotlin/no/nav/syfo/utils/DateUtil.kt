package no.nav.syfo.utils

import java.time.*
import java.time.format.DateTimeFormatter

private const val BREV_DATE_FORMAT_PATTERN = "dd. MMMM yyyy"

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

fun parsePDLDate(date: String): LocalDate {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    return LocalDate.parse(date, formatter)
}

fun formatDateForLetter(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern(BREV_DATE_FORMAT_PATTERN))
}

fun isFodselsdatoMindreEnn67Ar(fodselsdato: String?): Boolean {
    val parsedFodselsdato = fodselsdato?.let { parsePDLDate(it) }
    val isFodselsdatoMindreEnn67Ar = parsedFodselsdato == null || (Period.between(parsedFodselsdato, LocalDate.now()).years < 67)

    return isFodselsdatoMindreEnn67Ar
}

fun norwegianOffsetDateTime() = OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
