package no.nav.syfo.utils

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val BREV_DATE_FORMAT_PATTERN = "dd. MMMM yyyy"

fun LocalDate.isEqualOrBefore(other: LocalDate): Boolean {
    return this == other || this.isBefore(other)
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

fun isAlderMindreEnnGittAr(fodselsdato: String, maxAlder: Int): Boolean {
    val parsedFodselsdato = fodselsdato.let { parsePDLDate(it) }

    return Period.between(parsedFodselsdato, LocalDate.now()).years < maxAlder
}

fun norwegianOffsetDateTime() = OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
