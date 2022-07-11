package no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain

import java.time.LocalDate

data class Oppfolgingstilfelle39Uker(
    val aktorId: String,
    val arbeidsgiverperiodeTotalt: Int,
    val antallSykefravaersDagerTotalt: Int,
    val fom: LocalDate,
    val tom: LocalDate
)