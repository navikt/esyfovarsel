package no.nav.syfo.producer.arbeidsgivernotifikasjon

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

enum class OpprettNyBeskjedArbeidsgiverNotifikasjonMutationStatus(val status: String) {
    NY_BESKJED_VELLYKKET("NyBeskjedVellykket"),
    UGYLDIG_MERKELAPP("UgyldigMerkelapp"),
    UGYLDIG_MOTTAKER("UgyldigMottaker"),
    UKJENT_PRODUSENT("UkjentProdusent"),
    DUPLIKAT_ID_OG_MERKELAPP("DuplikatEksternIdOgMerkelapp"),
}
