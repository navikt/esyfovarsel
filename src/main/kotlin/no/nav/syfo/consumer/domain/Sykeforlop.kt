package no.nav.syfo.consumer.domain

import java.time.LocalDate
import java.util.ArrayList

data class Sykeforlop(
    var oppfolgingsdato: LocalDate,
    val sykmeldinger: List<SimpleSykmelding> = ArrayList()
)

data class SimpleSykmelding(
    val id: String,
    val fom: LocalDate,
    val tom: LocalDate
)
