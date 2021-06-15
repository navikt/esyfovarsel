package no.nav.syfo.utils

import java.time.LocalDate

fun LocalDate.isEqualOrAfter(other: LocalDate): Boolean {
    return this == other || this.isAfter(other)
}
