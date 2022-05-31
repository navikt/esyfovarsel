package no.nav.syfo.consumer.arbeidsgiverNotifikasjonProdusent

import java.io.Serializable

data class OpprettNyBeskjedArbeidsgiverNotifikasjonResponse(
    val data: Data?
)

data class NyBeskjed(
    val id: String?,
    val feilmelding: String?,
    val __typename: String?
) : Serializable

data class Data(
    val nyBeskjed: NyBeskjed,
)
