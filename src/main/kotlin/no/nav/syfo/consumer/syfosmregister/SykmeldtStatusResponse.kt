package no.nav.syfo.consumer.syfosmregister

import java.io.Serializable
import java.time.LocalDate

data class SykmeldtStatusResponse (
    val erSykmeldt: Boolean,
    val gradert: Boolean?,
    val fom: LocalDate?,
    val tom: LocalDate?
) : Serializable
