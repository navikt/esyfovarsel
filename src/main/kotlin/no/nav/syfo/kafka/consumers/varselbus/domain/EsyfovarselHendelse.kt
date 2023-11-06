package no.nav.syfo.kafka.consumers.varselbus.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.syfo.kafka.common.createObjectMapper
import org.apache.commons.cli.MissingArgumentException
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
    SM_AKTIVITETSPLIKT_STATUS_FORHANDSVARSEL,
    SM_AKTIVITETSPLIKT_STATUS_NY,
    SM_AKTIVITETSPLIKT_STATUS_UNNTAK,
    SM_AKTIVITETSPLIKT_STATUS_OPPFYLT,
    SM_AKTIVITETSPLIKT_STATUS_AUTOMATISK_OPPFYLT,
    SM_AKTIVITETSPLIKT_STATUS_IKKE_OPPFYLT,
    SM_AKTIVITETSPLIKT_STATUS_IKKE_AKTUELL,
    SM_AKTIVITETSPLIKT_STATUS_AVVENT,
    SM_FORHANDSVARSEL_STANS, // TODO: Slett denne når vi tar over aktivitetskrav-varselløypa fra iSYFO
}

fun ArbeidstakerHendelse.getSynligTom(): LocalDateTime? {
    val eventType = this.type
    if (eventType.isNotValidHendelseType()) {
        throw IllegalArgumentException(
            "${eventType.name} er ikke gyldig hendelse for å hente ut " +
                    "'synligTom'-felt",
        )
    }
    if (eventType.isAktivitetspliktType())
        return LocalDateTime.now().plusDays(30L)
    return if (eventType != HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV) this.motetidspunkt() else null
}

private fun ArbeidstakerHendelse.motetidspunkt(): LocalDateTime {
    this.data?.let { data ->
        val varseldata = data.toVarselData()
        val varselMotetidspunkt = varseldata.motetidspunkt
        return varselMotetidspunkt?.tidspunkt
            ?: throw NullPointerException("'tidspunkt'-felt er null i VarselDataMotetidspunkt-objekt")
    } ?: throw MissingArgumentException("Mangler datafelt i ArbeidstakerHendelse til MicrofrontendService")
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

fun HendelseType.isAktivitetspliktType() = this in listOf(
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_NY,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_FORHANDSVARSEL,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_UNNTAK,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_OPPFYLT,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_AUTOMATISK_OPPFYLT,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_IKKE_OPPFYLT,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_IKKE_AKTUELL,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_AVVENT
)

fun HendelseType.isDialogmoteType() = this in listOf(
    HendelseType.SM_DIALOGMOTE_INNKALT,
    HendelseType.SM_DIALOGMOTE_NYTT_TID_STED,
    HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV
)

fun ArbeidstakerHendelse.isNotEligibleForMikrofrontendProcessing() = this.type !in listOf(
    HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV,
    HendelseType.SM_DIALOGMOTE_INNKALT,
    HendelseType.SM_DIALOGMOTE_AVLYST,
    HendelseType.SM_DIALOGMOTE_REFERAT,
    HendelseType.SM_DIALOGMOTE_NYTT_TID_STED,
    HendelseType.SM_DIALOGMOTE_LEST,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_UNNTAK,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_OPPFYLT,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_AUTOMATISK_OPPFYLT,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_IKKE_OPPFYLT,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_IKKE_AKTUELL,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_AVVENT,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_NY,
    HendelseType.SM_AKTIVITETSPLIKT_STATUS_FORHANDSVARSEL
)

fun HendelseType.isNotValidHendelseType() = !this.isAktivitetspliktType() && !this.isDialogmoteType()
