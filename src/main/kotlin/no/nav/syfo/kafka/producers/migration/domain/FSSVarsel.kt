package no.nav.syfo.kafka.producers.migration.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class FSSVarsel(
    val uuid: String,
    val fnr: String,
    val aktorId: String,
    val type: String,
    val utsendingsdato: LocalDate,
    val opprettet: LocalDateTime,
    val sistEndret: LocalDateTime,
    val orgnummer: String?
)
