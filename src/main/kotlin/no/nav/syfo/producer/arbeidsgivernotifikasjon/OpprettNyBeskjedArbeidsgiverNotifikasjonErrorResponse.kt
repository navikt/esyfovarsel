package no.nav.syfo.producer.arbeidsgivernotifikasjon

data class OpprettNyBeskjedArbeidsgiverNotifikasjonErrorResponse(
    val errors: List<Error>,
)

data class Error(
    val message: String
)
