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
    val orgnummer: String
) : EsyfovarselHendelse

data class ArbeidstakerHendelse(
    override val type: HendelseType,
    override val ferdigstill: Boolean?,
    override var data: Any?,
    val arbeidstakerFnr: String,
    val orgnummer: String?
) : EsyfovarselHendelse

data class VarselData(
    val journalpost: VarselDataJournalpost? = null,
    val narmesteLeder: VarselDataNarmesteLeder? = null,
    val motetidspunkt: VarselDataMotetidspunkt? = null,
)

data class VarselDataJournalpost(
    val uuid: String,
    val id: String?
)

data class VarselDataNarmesteLeder(
    val navn: String?
)

data class VarselDataMotetidspunkt(
    val tidspunkt: LocalDateTime
)

enum class HendelseType {
    NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
    SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
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
    SM_DIALOGMOTE_LEST,
}

fun Any.toVarselData(): VarselData =
    objectMapper.readValue(
        this.toString(),
        VarselData::class.java
    )

