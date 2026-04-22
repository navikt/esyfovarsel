package no.nav.syfo.producer.arbeidsgivernotifikasjon

data class NotificationAgRequest(
    val query: String,
    val variables: Variables,
)

enum class EpostSendevinduTypes {
    LOEPENDE,
}

sealed interface Variables

data class NarmestelederVariablesCreate(
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
    val grupperingsid: String,
) : Variables

data class AltinnRessursVariablesCreate(
    val eksternId: String,
    val virksomhetsnummer: String,
    val lenke: String,
    val merkelapp: String,
    val tekst: String,
    val epostTittel: String,
    val epostHtmlBody: String,
    val sendevindu: EpostSendevinduTypes,
    val hardDeleteDate: String,
    val grupperingsid: String,
    val ressursId: String,
) : Variables

data class VariablesDelete(
    val merkelapp: String,
    val eksternId: String,
) : Variables
