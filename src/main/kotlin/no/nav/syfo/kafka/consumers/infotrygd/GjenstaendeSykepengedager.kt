package no.nav.syfo.kafka.consumers.infotrygd

import no.nav.syfo.utils.isEqualOrBefore
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.streams.toList

fun LocalDate.gjenstaendeSykepengedager(other: LocalDate): Int {
    // datesUntil teller med startdato og ikke sluttdato, mens vi ikke vil telle med startdato, men sluttdato, derfor er det lagt til en dag på begge datoene
    return if (this.isEqualOrBefore(other)) this
        .plusDays(1)
        .datesUntil(other.plusDays(1))
        .toList()
        .count(LocalDate::erIkkeHelg)
        .coerceAtLeast(0) else 0
}

private fun LocalDate.erIkkeHelg() = dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
