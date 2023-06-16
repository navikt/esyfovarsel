package no.nav.syfo.producer.arbeidsgivernotifikasjon.response.nyoppgave

import java.io.Serializable

data class NyOppgaveResponse(
    val data: Data?,
)

data class NyOppgave(
    val id: String?,
    val feilmelding: String?,
    val __typename: String?,
) : Serializable

data class Data(
    val nyOppgave: NyOppgave,
)

enum class NyoppgaveMutationStatus(val status: String) {
    NY_OPPGAVE_VELLYKKET("NyOppgaveVellykket"),
    UGYLDIG_MERKELAPP("UgyldigMerkelapp"),
    UGYLDIG_MOTTAKER("UgyldigMottaker"),
    UKJENT_PRODUSENT("UkjentProdusent"),
    DUPLIKAT_ID_OG_MERKELAPP("DuplikatEksternIdOgMerkelapp"),
}
