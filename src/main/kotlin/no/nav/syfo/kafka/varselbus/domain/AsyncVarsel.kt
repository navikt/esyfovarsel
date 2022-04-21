package no.nav.syfo.kafka.varselbus.domain

data class AsyncVarsel(
    val mottakerFnr: String,
    val extraContext: VarselContext,
    val extraContent: Any?
)

enum class VarselContext {
    SM_MOTEBEHOV_OPPRETTET,
    SM_MOTEBEHOV_LEST,
    SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
    SM_OPPFOLGINGSPLAN_LEST,
    NL_MOTEBEHOV_OPPRETTET,
    NL_MOTEBEHOV_LEST,
    NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
    NL_OPPFOLGINGSPLAN_LEST
}