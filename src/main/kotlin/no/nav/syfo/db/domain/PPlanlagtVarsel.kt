package no.nav.syfo.db.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class PPlanlagtVarsel(
    val uuid: String,
    val fnr: String,
    val orgnummer: String,
    val aktorId: String,
    val type: String,
    val utsendingsdato: LocalDate,
    val opprettet: LocalDateTime,
    val sistEndret: LocalDateTime
)
