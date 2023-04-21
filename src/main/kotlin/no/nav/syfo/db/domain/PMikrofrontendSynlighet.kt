package no.nav.syfo.db.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class PMikrofrontendSynlighet(
    val uuid: String,
    val synligFor: String,
    val tjeneste: String,
    val synligTom: LocalDate?,
    val opprettet: LocalDateTime,
    val sistEndret: LocalDateTime
)
