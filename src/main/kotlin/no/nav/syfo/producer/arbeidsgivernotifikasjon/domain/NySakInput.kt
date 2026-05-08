package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import com.apollo.graphql.NySakMutation
import com.apollo.graphql.type.AltinnRessursMottakerInput
import com.apollo.graphql.type.FutureTemporalInput
import com.apollo.graphql.type.MottakerInput
import com.apollo.graphql.type.NaermesteLederMottakerInput
import com.apollo.graphql.type.SaksStatus
import com.apollographql.apollo.api.Optional
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP
import no.nav.syfo.producer.arbeidsgivernotifikasjon.formatAsISO8601DateTime
import java.time.LocalDateTime

const val SAK_TYPE_DIALOGMOTE_MED_LEDER = "dialogmote-med-leder"
const val SAK_TYPE_DIALOGMOTE_UTEN_LEDER = "dialogmote-uten-leder"
const val SAK_TYPE_OPPFOLGING_MED_LEDER = "oppfolging-med-leder"

sealed interface NySakInput {
    val grupperingsid: String
    val merkelapp: String
    val virksomhetsnummer: String
    val ansattFnr: String
    val tittel: String
    val tilleggsinformasjon: String?
    val lenke: String
    val initiellStatus: SakStatus
    val nesteSteg: String?
    val overstyrStatustekstMed: String?
    val hardDeleteDate: LocalDateTime

    fun toNySakMutation(): NySakMutation

    fun toSakType(): String

    fun buildNySakMutation(mottakere: List<MottakerInput>): NySakMutation =
        NySakMutation(
            grupperingsid = grupperingsid,
            merkelapp = merkelapp,
            virksomhetsnummer = virksomhetsnummer,
            mottakere = mottakere,
            tittel = tittel,
            tilleggsinformasjon = Optional.presentIfNotNull(tilleggsinformasjon),
            lenke = Optional.present(lenke),
            initiellStatus = SaksStatus.valueOf(initiellStatus.name),
            nesteSteg = Optional.presentIfNotNull(nesteSteg),
            overstyrStatustekstMed = Optional.presentIfNotNull(overstyrStatustekstMed),
            hardDelete =
                Optional.present(
                    FutureTemporalInput(
                        den =
                            Optional.present(
                                hardDeleteDate.formatAsISO8601DateTime(),
                            ),
                    ),
                ),
        )
}

data class NySakNarmesteLederInput(
    override val grupperingsid: String,
    val narmestelederId: String,
    override val merkelapp: String,
    override val virksomhetsnummer: String,
    val narmesteLederFnr: String,
    override val ansattFnr: String,
    override val tittel: String,
    override val tilleggsinformasjon: String? = null,
    override val lenke: String,
    override val initiellStatus: SakStatus,
    override val nesteSteg: String? = null,
    override val overstyrStatustekstMed: String? = null,
    override val hardDeleteDate: LocalDateTime,
) : NySakInput {
    override fun toNySakMutation(): NySakMutation =
        buildNySakMutation(
            listOf(
                MottakerInput(
                    naermesteLeder =
                        Optional.present(
                            NaermesteLederMottakerInput(
                                naermesteLederFnr = narmesteLederFnr,
                                ansattFnr = ansattFnr,
                            ),
                        ),
                ),
            ),
        )

    override fun toSakType(): String =
        when (merkelapp) {
            ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP -> SAK_TYPE_DIALOGMOTE_MED_LEDER
            ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP -> SAK_TYPE_OPPFOLGING_MED_LEDER
            else -> throw IllegalStateException("Unknown merkelapp for NySakNarmesteLederInput: $merkelapp")
        }
}

data class NySakAltinnInput(
    override val grupperingsid: String,
    override val merkelapp: String,
    override val virksomhetsnummer: String,
    override val ansattFnr: String,
    override val tittel: String,
    override val tilleggsinformasjon: String? = null,
    override val lenke: String,
    override val initiellStatus: SakStatus,
    override val nesteSteg: String? = null,
    override val overstyrStatustekstMed: String? = null,
    override val hardDeleteDate: LocalDateTime,
    val ressursId: String,
) : NySakInput {
    override fun toNySakMutation(): NySakMutation =
        buildNySakMutation(
            listOf(
                MottakerInput(
                    altinnRessurs =
                        Optional.present(
                            AltinnRessursMottakerInput(
                                ressursId = ressursId,
                            ),
                        ),
                ),
            ),
        )

    override fun toSakType(): String = SAK_TYPE_DIALOGMOTE_UTEN_LEDER
}

enum class SakStatus {
    MOTTATT,
    UNDER_BEHANDLING,
    FERDIG,
}
