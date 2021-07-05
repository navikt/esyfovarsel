package no.nav.syfo.consumer.syfosmregister

import java.time.LocalDate

data class SykmeldtStatusRequest (
    val fnr: String,
    val dato: LocalDate
)
