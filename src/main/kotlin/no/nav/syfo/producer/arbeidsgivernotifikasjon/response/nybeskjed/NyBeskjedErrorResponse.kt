package no.nav.syfo.producer.arbeidsgivernotifikasjon.response.nybeskjed

data class NyBeskjedErrorResponse(
    val errors: List<Error>,
)

data class Error(
    val message: String,
)
