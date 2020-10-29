package no.nav.syfo.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun erDatoIPerioden(dato: LocalDate, fom: LocalDate, tom: LocalDate): Boolean {
    return dato.isEqual(fom) || dato.isEqual(tom) || iDatoIntervall(dato, tom, fom)
}

private fun iDatoIntervall(dato: LocalDate, tom: LocalDate, fom: LocalDate): Boolean {
    return dato.isAfter(fom) && dato.isBefore(tom)
}

fun antallDagerMellom(fom: LocalDate, tom: LocalDate): Int {
    return ChronoUnit.DAYS.between(fom, tom).toInt() - 1
}

fun antallDager(fom: LocalDate, tom: LocalDate): Int {
    return ChronoUnit.DAYS.between(fom, tom).toInt()
}

fun compare(localDate: LocalDate): LocalDateComparator {
    return LocalDateComparator(localDate)
}

class LocalDateComparator(private val localDate: LocalDate) {
    fun isEqualOrAfter(other: LocalDate?): Boolean {
        return !localDate.isBefore(other)
    }

    fun isBeforeOrEqual(other: LocalDate?): Boolean {
        return !localDate.isAfter(other)
    }

    fun isNotBetween(fom: LocalDate?, tom: LocalDate?): Boolean {
        return localDate.isBefore(fom) || localDate.isAfter(tom)
    }

    fun isBetweenOrEqual(fom: LocalDate?, tom: LocalDate?): Boolean {
        return !isNotBetween(fom, tom)
    }

    fun isBetween(fom: LocalDate?, tom: LocalDate?): Boolean {
        return localDate.isAfter(fom) && localDate.isBefore(tom)
    }
}
