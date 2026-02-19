package no.nav.syfo.consumer.syfosmregister

import java.io.Serializable
import java.time.LocalDate

data class SykmeldtStatusRequest(
    val fnr: String,
    val dato: LocalDate,
) : Serializable

data class SykmeldtStatus(
    val erSykmeldt: Boolean,
    val gradert: Boolean?,
    val fom: LocalDate?,
    val tom: LocalDate?,
) : Serializable
