package no.nav.syfo.db.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class PMaksDato(
    val id: String,
    val fnr: String,
    val forelopig_beregnet_slutt: LocalDate,
    val utbetalt_tom: LocalDate,
    val gjenstaende_sykedager: String,
    val opprettet: LocalDateTime,
)
