package no.nav.syfo.kafka.varselbus.domain

import java.io.Serializable

data class EsyfovarselHendelse(
    val mottakerFnr: String,
    val type: HendelseType,
    var data: Any?
) : Serializable

enum class HendelseType {
    NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
    NL_OPPFOLGINGSPLAN_OPPRETTET
}
