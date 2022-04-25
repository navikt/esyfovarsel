package no.nav.syfo.kafka.varselbus.domain

import java.io.Serializable
import java.util.*

data class EsyfovarselHendelse(
    val mottakerFnr: String,
    val type: HendelseType,
    val data: Any?
) : Serializable

enum class HendelseType {
    NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
    NL_OPPFOLGINGSPLAN_OPPRETTET
}


