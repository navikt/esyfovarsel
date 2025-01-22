package no.nav.syfo.producer.arbeidsgivernotifikasjon

data class NotificationAgRequest(
    val query: String,
    val variables: Variables
)

enum class EpostSendevinduTypes {
    LOEPENDE
}

sealed interface Variables
data class VariablesCreate(
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
    val hardDeleteDate: String,
) : Variables

data class VariablesDelete(
    val merkelapp: String,
    val eksternId: String
) : Variables
