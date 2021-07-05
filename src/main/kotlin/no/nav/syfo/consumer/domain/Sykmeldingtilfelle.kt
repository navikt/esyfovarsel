package no.nav.syfo.consumer.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class Sykmeldingtilfelle(
    val ressursId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val opprettet: LocalDateTime
)
