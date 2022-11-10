package no.nav.syfo.db.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class PUtbetaling(
    val id: UUID,
    val fnr: String,
    val utbetaltTom: LocalDate,
    val forelopigBeregnetSlutt: LocalDate,
    val gjenstaendeSykedager: Int,
    val opprettet: LocalDateTime,
)
