package no.nav.syfo.producer.arbeidsgivernotifikasjon.response.nyoppgave

data class NyOppgaveErrorResponse(
    val errors: List<Error>,
)

data class Error(
    val message: String,
)
