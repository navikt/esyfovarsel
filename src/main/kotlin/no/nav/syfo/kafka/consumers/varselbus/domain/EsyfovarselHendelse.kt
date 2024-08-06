package no.nav.syfo.kafka.consumers.varselbus.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.syfo.kafka.common.createObjectMapper
import java.io.Serializable
import java.time.LocalDateTime

private val objectMapper = createObjectMapper()

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed interface EsyfovarselHendelse : Serializable {
    val type: HendelseType
    val ferdigstill: Boolean?
    var data: Any?
}

data class NarmesteLederHendelse(
    override val type: HendelseType,
    override val ferdigstill: Boolean?,
    override var data: Any?,
    val narmesteLederFnr: String,
    val arbeidstakerFnr: String,
    val orgnummer: String,
) : EsyfovarselHendelse

data class ArbeidstakerHendelse(
    override val type: HendelseType,
    override val ferdigstill: Boolean?,
    override var data: Any?,
    val arbeidstakerFnr: String,
    val orgnummer: String?,
) : EsyfovarselHendelse

data class VarselData(
    val journalpost: VarselDataJournalpost? = null,
    val narmesteLeder: VarselDataNarmesteLeder? = null,
    val motetidspunkt: VarselDataMotetidspunkt? = null,
    val aktivitetskrav: VarselDataAktivitetskrav? = null,
)

data class VarselDataJournalpost(
    val uuid: String,
    val id: String?,
)

data class VarselDataNarmesteLeder(
    val navn: String?,
)

data class VarselDataMotetidspunkt(
    val tidspunkt: LocalDateTime,
)

data class VarselDataAktivitetskrav(
    val sendForhandsvarsel: Boolean,
    val enableMicrofrontend: Boolean,
    val extendMicrofrontendDuration: Boolean,
)

data class VarselDataMotebehovTilbakemelding(
    val tilbakemelding: String,
)

enum class HendelseType {
    NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
    SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
    NL_DIALOGMOTE_SVAR_MOTEBEHOV,
    SM_DIALOGMOTE_SVAR_MOTEBEHOV,
    NL_DIALOGMOTE_MOTEBEHOV_TILBAKEMELDING,
    SM_DIALOGMOTE_MOTEBEHOV_TILBAKEMELDING,
    SM_MER_VEILEDNING,
    NL_DIALOGMOTE_INNKALT,
    SM_DIALOGMOTE_INNKALT,
    NL_DIALOGMOTE_AVLYST,
    SM_DIALOGMOTE_AVLYST,
    NL_DIALOGMOTE_REFERAT,
    SM_DIALOGMOTE_REFERAT,
    NL_DIALOGMOTE_NYTT_TID_STED,
    SM_DIALOGMOTE_NYTT_TID_STED,
    SM_DIALOGMOTE_LEST,
    SM_AKTIVITETSPLIKT,
    SM_ARBEIDSUFORHET_FORHANDSVARSEL,
    SM_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING,
    SM_FORHANDSVARSEL_MANGLENDE_MEDVIRKNING,
}

fun ArbeidstakerHendelse.getSynligTom(): LocalDateTime? {
    val eventType = this.type
    if (eventType.isNotValidHendelseType()) {
        throw IllegalArgumentException("${eventType.name} er ikke gyldig hendelse for å hente ut 'synligTom'-felt")
    }
    if (eventType.isAktivitetspliktType()) {
        return LocalDateTime.now().plusDays(30L)
    }
    if (eventType.isMerOppfolgingType()) {
        return LocalDateTime.now().plusWeeks(13)
    }
    return if (eventType != HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV) this.motetidspunkt() else null
}

private fun ArbeidstakerHendelse.motetidspunkt(): LocalDateTime {
    this.data?.let { data ->
        val varseldata = data.toVarselData()
        val varselMotetidspunkt = varseldata.motetidspunkt
        return varselMotetidspunkt?.tidspunkt
            ?: throw NullPointerException("'tidspunkt'-felt er null i VarselDataMotetidspunkt-objekt")
    } ?: throw IllegalArgumentException("Mangler datafelt i ArbeidstakerHendelse til MicrofrontendService")
}

fun Any.toVarselData(): VarselData =
    objectMapper.readValue(
        this.toString(),
        VarselData::class.java,
    )

fun EsyfovarselHendelse.isArbeidstakerHendelse(): Boolean {
    return this is ArbeidstakerHendelse
}

fun EsyfovarselHendelse.toNarmestelederHendelse(): NarmesteLederHendelse {
    return if (this is NarmesteLederHendelse) {
        this
    } else {
        throw IllegalArgumentException("Wrong type of EsyfovarselHendelse, should be of type NarmesteLederHendelse")
    }
}

fun EsyfovarselHendelse.toArbeidstakerHendelse(): ArbeidstakerHendelse {
    return if (this is ArbeidstakerHendelse) {
        this
    } else {
        throw IllegalArgumentException("Wrong type of EsyfovarselHendelse, should be of type ArbeidstakerHendelse")
    }
}

fun EsyfovarselHendelse.skalFerdigstilles() =
    ferdigstill ?: false

fun HendelseType.isAktivitetspliktType() = this == HendelseType.SM_AKTIVITETSPLIKT
fun HendelseType.isMerOppfolgingType() = this == HendelseType.SM_MER_VEILEDNING

fun HendelseType.isDialogmoteInnkallingType() = this in listOf(
    HendelseType.SM_DIALOGMOTE_INNKALT,
    HendelseType.SM_DIALOGMOTE_NYTT_TID_STED,
    HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV,
)

fun HendelseType.isDialogmoteType() =
    this.isDialogmoteInnkallingType() or (this in listOf(
        HendelseType.SM_DIALOGMOTE_AVLYST,
        HendelseType.SM_DIALOGMOTE_REFERAT,
        HendelseType.SM_DIALOGMOTE_LEST,
    ))

fun ArbeidstakerHendelse.notCorrectMikrofrontendType() =
    !(this.type.isDialogmoteType() or this.type.isAktivitetspliktType() or this.type.isMerOppfolgingType())

fun ArbeidstakerHendelse.isAktivitetspliktWithFerdigstilling() =
    (this.type.isAktivitetspliktType() and (this.ferdigstill == true))

fun ArbeidstakerHendelse.isMerOppfolgingWithFerdigstilling() =
    (this.type.isMerOppfolgingType() and (this.ferdigstill == true))

fun ArbeidstakerHendelse.isNotEligibleForMikrofrontendProcessing(): Boolean {
    return this.notCorrectMikrofrontendType() or isAktivitetspliktWithFerdigstilling() or isMerOppfolgingWithFerdigstilling()
}

fun HendelseType.isNotValidHendelseType() =
    !this.isAktivitetspliktType() && !this.isDialogmoteInnkallingType() && !this.isMerOppfolgingType()
