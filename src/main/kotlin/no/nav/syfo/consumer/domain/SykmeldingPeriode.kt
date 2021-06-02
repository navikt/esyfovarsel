package no.nav.syfo.consumer.domain

import com.fasterxml.jackson.annotation.JsonFormat

@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
data class SykmeldingPeriode(
    var fom: String? = null,
    var tom: String? = null,
    val gradert: Gradert? = null
)
