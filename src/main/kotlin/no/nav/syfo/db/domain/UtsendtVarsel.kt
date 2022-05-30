package no.nav.syfo.db.domain

import java.time.LocalDateTime

data class UtsendtVarsel (
    val fnr: String,
    val aktorId: String?,
    val ansattFnr: String?,
    val orgnummer: String?,
    val type: String,
    val utsendtTidspunkt: LocalDateTime,
    val planlagtVarselId: String?
)
