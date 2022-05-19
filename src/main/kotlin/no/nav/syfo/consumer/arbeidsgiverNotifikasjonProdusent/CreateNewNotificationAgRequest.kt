package no.nav.syfo.consumer.arbeidsgiverNotifikasjonProdusent

data class CreateNewNotificationAgRequest(
    val query: String,
    val variables: Variables
)

enum class EpostSendevinduTypes {
    LOEPENDE, DAGTID_IKKE_SOENDAG, NKS_AAPNINGSTID;
}

data class Variables(
    val eksternId: String,
    val virksomhetsnummer: String,
    val lenke: String,
    val naermesteLederFnr: String,
    val ansattFnr: String,
    val merkelapp: String,
    val tekst: String,

    val epostadresse: String,
    val epostTittel: String,
    val epostHtmlBody: String,
    val sendevindu: EpostSendevinduTypes,
)
