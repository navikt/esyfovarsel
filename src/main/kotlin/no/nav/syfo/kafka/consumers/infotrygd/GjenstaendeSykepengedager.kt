package no.nav.syfo.kafka.consumers.infotrygd

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.streams.toList

fun LocalDate.gjenstaendeSykepengedager(other: LocalDate): Int {
    return this
        .plusDays(1)
        .datesUntil(other.plusDays(1))
        .toList()
        .count(LocalDate::erIkkeHelg)
        .coerceAtLeast(0)
}

private fun LocalDate.erIkkeHelg() = dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)