package no.nav.syfo.producer.arbeidsgivernotifikasjon.response.nybeskjed

import java.io.Serializable

data class NyBeskjedResponse(
    val data: Data?,
)

data class NyBeskjed(
    val id: String?,
    val feilmelding: String?,
    val __typename: String?,
) : Serializable

data class Data(
    val nyBeskjed: NyBeskjed,
)

enum class NyBeskjedMutationStatus(val status: String) {
    NY_BESKJED_VELLYKKET("NyBeskjedVellykket"),
    UGYLDIG_MERKELAPP("UgyldigMerkelapp"),
    UGYLDIG_MOTTAKER("UgyldigMottaker"),
    UKJENT_PRODUSENT("UkjentProdusent"),
    DUPLIKAT_ID_OG_MERKELAPP("DuplikatEksternIdOgMerkelapp"),
}
