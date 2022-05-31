package no.nav.syfo.consumer.arbeidsgiverNotifikasjonProdusent

data class OpprettNyBeskjedArbeidsgiverNotifikasjonErrorResponse(
    val errors: List<Error>,
)

data class Error(
    val message: String
)
