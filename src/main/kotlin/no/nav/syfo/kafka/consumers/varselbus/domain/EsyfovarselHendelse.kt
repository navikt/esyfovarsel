package no.nav.syfo.kafka.consumers.varselbus.domain

import java.io.Serializable

interface EsyfovarselHendelse : Serializable {
    val mottaker: Mottaker
    val type: HendelseType
    var data: Any?
}

interface Mottaker : Serializable

data class SykmeldtMottaker(
    val mottakerFnr: String
) : Mottaker, Serializable

data class NarmesteLederMottaker(
    val mottakerFnr: String,
    val orgnummer: String,
    val ansattFnr: String,
) : Mottaker, Serializable

data class NarmesteLederHendelse(
    override val mottaker: NarmesteLederMottaker,
    override val type: HendelseType,
    override var data: Any?
) : EsyfovarselHendelse

data class SykmeldtHendelse(
    override val mottaker: SykmeldtMottaker,
    override val type: HendelseType,
    override var data: Any?
) : EsyfovarselHendelse

enum class HendelseType {
    NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
    NL_OPPFOLGINGSPLAN_OPPRETTET,
    NL_DIALOGMOTE_SVAR_MOTEBEHOV,
    SM_DIALOGMOTE_SVAR_MOTEBEHOV,
}

fun HendelseType.toDineSykmeldteHendelseType(): DineSykmeldteHendelseType {
    return when (this) {
        HendelseType.NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> DineSykmeldteHendelseType.OPPFOLGINGSPLAN_TIL_GODKJENNING
        HendelseType.NL_OPPFOLGINGSPLAN_OPPRETTET -> DineSykmeldteHendelseType.OPPFOLGINGSPLAN_OPPRETTET
        HendelseType.NL_DIALOGMOTE_SVAR_MOTEBEHOV -> DineSykmeldteHendelseType.DIALOGMOTE_SVAR_BEHOV
        else -> {throw IllegalArgumentException("Kan ikke mappe ${this.name} til en DineSykmeldteHendelsesType")}
    }
}
