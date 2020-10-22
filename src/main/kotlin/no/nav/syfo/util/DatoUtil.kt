package no.nav.syfo.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun erDatoIPerioden(dato: LocalDate, fom: LocalDate, tom: LocalDate): Boolean {
    return dato.isEqual(fom) || dato.isEqual(tom) || iDatoIntervall(dato, tom, fom)
}

private fun iDatoIntervall(dato: LocalDate, tom: LocalDate, fom: LocalDate): Boolean {
    return dato.isAfter(fom) && dato.isBefore(tom)
}

fun antallDagerMellom(tidligst: LocalDate?, eldst: LocalDate?): Int {
    return ChronoUnit.DAYS.between(tidligst, eldst).toInt() - 1
}

fun antallDager(fom: LocalDate?, tom: LocalDate?): Int {
    return ChronoUnit.DAYS.between(fom, tom).toInt()
}