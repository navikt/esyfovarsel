package no.nav.syfo.kafka.consumers.varselbus.domain

enum class DineSykmeldteHendelseType {
    OPPFOLGINGSPLAN_OPPRETTET,
    OPPFOLGINGSPLAN_TIL_GODKJENNING,
    DIALOGMOTE_SVAR_BEHOV,
    AKTIVITETSKRAV,
    DIALOGMOTE_INNKALLING,
    DIALOGMOTE_AVLYSNING,
    DIALOGMOTE_ENDRING,
    DIALOGMOTE_REFERAT,
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
