package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import com.apollo.graphql.NySakMutation
import com.apollo.graphql.type.FutureTemporalInput
import com.apollo.graphql.type.MottakerInput
import com.apollo.graphql.type.NaermesteLederMottakerInput
import com.apollo.graphql.type.SaksStatus
import com.apollographql.apollo.api.Optional
import java.time.LocalDateTime
import no.nav.syfo.producer.arbeidsgivernotifikasjon.formatAsISO8601DateTime

data class NySakInput(
    val grupperingsid: String,
    val narmestelederId: String,
    val merkelapp: String,
    val virksomhetsnummer: String,
    val narmesteLederFnr: String,
    val ansattFnr: String,
    val tittel: String,
    val tilleggsinformasjon: String? = null,
    val lenke: String,
    val initiellStatus: SakStatus,
    val nesteSteg: String? = null,
    val overstyrStatustekstMed: String? = null,
    val hardDeleteDate: LocalDateTime,
)

fun NySakInput.toNySakMutation(): NySakMutation =
    NySakMutation(
        grupperingsid = grupperingsid,
        merkelapp = merkelapp,
        virksomhetsnummer = virksomhetsnummer,
        mottakere =
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

enum class SakStatus {
    MOTTATT,
    UNDER_BEHANDLING,
    FERDIG,
}
