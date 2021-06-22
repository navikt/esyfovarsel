package no.nav.syfo.consumer.domain

import java.time.LocalDate

data class Sykmeldingtilfelle(
    val ressursId: String,
    val fom: LocalDate,
    val tom: LocalDate
)
