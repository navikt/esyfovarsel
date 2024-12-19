package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import com.apollo.graphql.NySakMutation
import com.apollo.graphql.type.FutureTemporalInput
import com.apollo.graphql.type.MottakerInput
import com.apollo.graphql.type.NaermesteLederMottakerInput
import com.apollo.graphql.type.SaksStatus
import com.apollographql.apollo.api.Optional
import no.nav.syfo.producer.arbeidsgivernotifikasjon.formatAsISO8601DateTime
import java.time.LocalDateTime

// Ny sak som vises til arbeidsgiver
data class NySakInput(
    val grupperingsid: String,
    val merkelapp: String,
    val virksomhetsnummer: String,
    val narmesteLederFnr: String,
    val ansattFnr: String,
    val tittel: String,
    val tilleggsinformasjon: String? = null,
    val lenke: String,
    val initiellStatus: SaksStatus,
    val nesteSteg: String? = null,
    val tidspunkt: LocalDateTime,
    val overstyrStatustekstMed: String? = null,
    val hardDeleteDate: LocalDateTime,
)

fun NySakInput.toNySakMutation(): NySakMutation {
    return NySakMutation(
        grupperingsid = grupperingsid,
        merkelapp = merkelapp,
        virksomhetsnummer = virksomhetsnummer,
        mottakere = listOf(
            MottakerInput(
                naermesteLeder = Optional.present(
                    NaermesteLederMottakerInput(
                        naermesteLederFnr = narmesteLederFnr,
                        ansattFnr = ansattFnr
                    )
                )
            )
        ),
        tittel = tittel,
        tilleggsinformasjon = Optional.presentIfNotNull(tilleggsinformasjon),
        lenke = Optional.present(lenke),
        initiellStatus = initiellStatus,
        nesteSteg = Optional.presentIfNotNull(nesteSteg),
        tidspunkt = Optional.presentIfNotNull(tidspunkt.formatAsISO8601DateTime()),
        overstyrStatustekstMed = Optional.presentIfNotNull(overstyrStatustekstMed),
        hardDelete = Optional.present(
            FutureTemporalInput(
                den = Optional.present(
                    hardDeleteDate.formatAsISO8601DateTime()
                )
            )
        ),
    )
}