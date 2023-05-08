package no.nav.syfo.db.domain

import java.time.LocalDateTime

data class PUtsendtVarselFeilet(
    val uuid: String,
    val fnr: String,
    val narmesteLederFnr: String?,
    val orgnummer: String?,
    val type: String,
    val kanal: String?,
    val brukernotifikasjonerMeldingType: String?,
    val utsendtForsokTidspunkt: LocalDateTime,
    val eksternReferanse: String?,
    val feilmelding: String?,
    val journalpostId: String?,
)
