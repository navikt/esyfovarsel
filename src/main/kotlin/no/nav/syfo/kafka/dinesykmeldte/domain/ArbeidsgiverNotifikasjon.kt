package no.nav.syfo.kafka.dinesykmeldte.domain

import java.time.LocalDateTime

data class ArbeidsgiverNotifikasjon(
    val varselId: String,
    val virksomhetsnummer: String,
    val url: String,
    val naermesteLederFnr: String,
    val ansattFnr: String,
    val messageText: String,
    val narmesteLederEpostadresse: String,
    val emailTitle: String,
    val emailBody: String,
    val hardDeleteDate: LocalDateTime,
)
