package no.nav.syfo.kafka.consumers.varselbus.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.Serializable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed interface EsyfovarselHendelse : Serializable {
    val type: HendelseType
    var data: Any?
}

data class NarmesteLederHendelse(
    override val type: HendelseType,
    override var data: Any?,
    val narmesteLederFnr: String,
    val arbeidstakerFnr: String,
    val orgnummer: String
) : EsyfovarselHendelse

data class ArbeidstakerHendelse(
    override val type: HendelseType,
    override var data: Any?,
    val arbeidstakerFnr: String,
    val orgnummer: String?
) : EsyfovarselHendelse

enum class HendelseType {
    NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
    NL_OPPFOLGINGSPLAN_OPPRETTET,
    NL_DIALOGMOTE_SVAR_MOTEBEHOV,
    SM_DIALOGMOTE_SVAR_MOTEBEHOV,
    SM_MER_VEILEDNING,
    NL_DIALOGMOTE_INNKALT,
    SM_DIALOGMOTE_INNKALT,
    NL_DIALOGMOTE_AVLYST,
    SM_DIALOGMOTE_AVLYST,
    NL_DIALOGMOTE_REFERAT,
    SM_DIALOGMOTE_REFERAT,
    NL_DIALOGMOTE_NYTT_TID_STED,
    SM_DIALOGMOTE_NYTT_TID_STED,
}

fun HendelseType.toDineSykmeldteHendelseType(): DineSykmeldteHendelseType {
    return when (this) {
        HendelseType.NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> DineSykmeldteHendelseType.OPPFOLGINGSPLAN_TIL_GODKJENNING
        HendelseType.NL_OPPFOLGINGSPLAN_OPPRETTET -> DineSykmeldteHendelseType.OPPFOLGINGSPLAN_OPPRETTET
        HendelseType.NL_DIALOGMOTE_SVAR_MOTEBEHOV -> DineSykmeldteHendelseType.DIALOGMOTE_SVAR_BEHOV
        HendelseType.NL_DIALOGMOTE_INNKALT -> DineSykmeldteHendelseType.DIALOGMOTE_INNKALLING
        HendelseType.NL_DIALOGMOTE_AVLYST -> DineSykmeldteHendelseType.DIALOGMOTE_AVLYSNING
        HendelseType.NL_DIALOGMOTE_NYTT_TID_STED -> DineSykmeldteHendelseType.DIALOGMOTE_ENDRING
        HendelseType.NL_DIALOGMOTE_REFERAT -> DineSykmeldteHendelseType.DIALOGMOTE_REFERAT
        else -> {
            throw IllegalArgumentException("Kan ikke mappe ${this.name} til en DineSykmeldteHendelsesType")
        }
    }
}
