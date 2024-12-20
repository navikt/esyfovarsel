package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import com.apollo.graphql.NyKalenderavtaleMutation
import com.apollo.graphql.OppdaterKalenderavtaleMutation
import com.apollo.graphql.type.FutureTemporalInput
import com.apollo.graphql.type.HardDeleteUpdateInput
import com.apollo.graphql.type.KalenderavtaleTilstand
import com.apollo.graphql.type.MottakerInput
import com.apollo.graphql.type.NaermesteLederMottakerInput
import com.apollo.graphql.type.NyTidStrategi
import com.apollographql.apollo.api.Optional
import no.nav.syfo.db.domain.PKalenderInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.formatAsISO8601DateTime
import java.time.LocalDateTime

/**
 * Kalenderavtale som sendes til arbeidsgiver
 *
 * @param virksomhetsnummer Organisasjonsnummeret til virksomheten som skal motta kalenderavtalen.
 * @param grupperingsid Grupperings-ID-en knytter denne kalenderavtalen til en sak med samme grupperings-ID og merkelapp. Det vises ikke til brukere. Saksnummer er en naturlig grupperings-ID.
 * @param merkelapp Merkelapp for kalenderavtalen. Er typisk navnet på ytelse eller lignende. Den vises ikke til brukeren, men brukes i kombinasjon med grupperings-ID for å koble kalenderavtalen til sak.
 * @param eksternId Den eksterne ID-en brukes for å unikt identifisere en notifikasjon. Den må være unik for merkelappen.
 * @param tekst Teksten som vises til brukeren.
 * @param ansattFnr Fødselsnummeret til den sykmeldte
 * @param narmesteLederFnr Fødselsnummeret til nærmeste leder
 * @param startTidspunkt Når avtalen starter.
 * @param sluttTidspunkt Når avtalen slutter (valgfritt).
 * @param kalenderavtaleTilstand Tilstanden til avtalen.
 * @param hardDeleteDate Når avtalen skal slettes.
 */
data class NyKalenderInput(
    val sakId: String,
    val virksomhetsnummer: String,
    val grupperingsid: String,
    val merkelapp: String,
    val eksternId: String,
    val tekst: String,
    val ansattFnr: String,
    val lenke: String,
    val narmesteLederFnr: String,
    val startTidspunkt: LocalDateTime,
    val sluttTidspunkt: LocalDateTime?,
    val kalenderavtaleTilstand: KalenderTilstand,
    val hardDeleteDate: LocalDateTime,
)

fun NyKalenderInput.toNyKalenderavtaleMutation(): NyKalenderavtaleMutation {
    return NyKalenderavtaleMutation(
        virksomhetsnummer = virksomhetsnummer,
        grupperingsid = grupperingsid,
        merkelapp = merkelapp,
        eksternId = eksternId,
        tekst = tekst,
        lenke = lenke,
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
        startTidspunkt = startTidspunkt.formatAsISO8601DateTime(),
        sluttTidspunkt = Optional.presentIfNotNull(sluttTidspunkt?.formatAsISO8601DateTime()),
        lokasjon = Optional.absent(),
        erDigitalt = Optional.absent(),
        tilstand = Optional.present(KalenderavtaleTilstand.valueOf(kalenderavtaleTilstand.name)),
        eksterneVarsler = listOf(),
        paaminnelse = Optional.absent(),
        hardDelete = Optional.present(
            FutureTemporalInput(
                den = Optional.present(
                    hardDeleteDate.formatAsISO8601DateTime()
                )
            )
        ),
    )
}

fun NyKalenderInput.toPKalenderInput(kalenderId: String): PKalenderInput {
    return PKalenderInput(
        eksternId = eksternId,
        sakId = sakId,
        kalenderId = kalenderId,
        tekst = tekst,
        startTidspunkt = startTidspunkt,
        sluttTidspunkt = sluttTidspunkt,
        kalenderavtaleTilstand = kalenderavtaleTilstand,
        hardDeleteDate = hardDeleteDate,
    )
}


/**
 * @param id ID-en til kalenderavtalen som skal oppdateres.
 * @param nyTilstand Den nye tilstanden til avtalen.
 * @param nyTekst Den nye teksten som skal vises til brukeren.
 * @param nyLenke Den nye lenken som brukeren skal føres til.
 * @param hardDeleteTidspunkt Når avtalen skal slettes.
 */
data class OppdaterKalenderInput(
    val id: String,
    val nyTilstand: KalenderTilstand,
    val nyTekst: String,
    val nyLenke: String? = null,
    val hardDeleteTidspunkt: LocalDateTime,
)

fun OppdaterKalenderInput.toOppdaterKalenderavtaleMutation(): OppdaterKalenderavtaleMutation {
    return OppdaterKalenderavtaleMutation(
        id = id,
        eksterneVarsler = listOf(),
        paaminnelse = Optional.absent(),
        nyTilstand = Optional.present(KalenderavtaleTilstand.valueOf(nyTilstand.name)),
        nyLokasjon = Optional.absent(),
        nyLenke =  Optional.absent(),
        nyTekst = Optional.present(nyTekst),
        nyErDigitalt = Optional.absent(),
        hardDelete = Optional.present(
            HardDeleteUpdateInput(
                nyTid = FutureTemporalInput(
                    den = Optional.present(hardDeleteTidspunkt.formatAsISO8601DateTime()),
                ),
                strategi = NyTidStrategi.OVERSKRIV,
            )
        ),
    )
}

enum class KalenderTilstand {
    VENTER_SVAR_FRA_ARBEIDSGIVER,
    ARBEIDSGIVER_VIL_AVLYSE,
    ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED,
    ARBEIDSGIVER_HAR_GODTATT,
    AVLYST,
}
