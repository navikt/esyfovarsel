package no.nav.syfo.consumer.domain

import java.time.LocalDate

data class Sykeforlop(
    val ressursIds: MutableSet<String>,
    var fom: LocalDate,
    var tom: LocalDate
)
