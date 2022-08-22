package no.nav.syfo.db.domain

import java.time.LocalDateTime

data class PUtsendtVarsel(
    val uuid: String,
    val fnr: String,
    val aktorId: String?,
    val narmesteLederFnr: String?,
    val orgnummer: String?,
    val type: String,
    val kanal: String?,
    val utsendtTidspunkt: LocalDateTime,
    val planlagtVarselId: String?
)
