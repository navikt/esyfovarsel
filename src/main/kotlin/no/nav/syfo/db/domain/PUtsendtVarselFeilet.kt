package no.nav.syfo.db.domain

import java.time.LocalDateTime

data class PUtsendtVarselFeilet(
    val uuid: String,
    val uuidEksternReferanse: String?,
    val arbeidstakerFnr: String,
    val narmesteLederFnr: String?,
    val orgnummer: String?,
    val hendelsetypeNavn: String,
    val arbeidsgivernotifikasjonMerkelapp: String?,
    val brukernotifikasjonerMeldingType: String?,
    val journalpostId: String?,
    val kanal: String?,
    val feilmelding: String?,
    val utsendtForsokTidspunkt: LocalDateTime,
)
