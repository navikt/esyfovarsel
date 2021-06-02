package no.nav.syfo.consumer.domain

import java.time.LocalDate

data class Sykmeldingtilfelle(
    val ressursId: String,
//    val datoer: List<LocalDate>,
    val fom: LocalDate,
    val tom: LocalDate
)
