package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import java.time.LocalDateTime

// Varsler (oppgaver og beskjeder) som sendes til arbeidsgiver
data class ArbeidsgiverNotifikasjon(
    val varselId: String,
    val virksomhetsnummer: String,
    val url: String,
    val narmesteLederFnr: String,
    val ansattFnr: String,
    val messageText: String,
    val narmesteLederEpostadresse: String,
    val merkelapp: String,
    val emailTitle: String,
    val emailBody: String,
    val hardDeleteDate: LocalDateTime,
)

data class ArbeidsgiverDeleteNotifikasjon(
    val merkelapp: String,
    val eksternReferanse: String
)
