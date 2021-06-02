package no.nav.syfo.consumer.domain

import java.time.LocalDate

data class Sykeforlop(
    val ressursIds: MutableList<String>,
    val fom: LocalDate,
    var tom: LocalDate
)
