package no.nav.syfo.kafka.dinesykmeldte.domain

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
)
