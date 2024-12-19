package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import com.apollo.graphql.NyKalenderavtaleMutation
import com.apollo.graphql.type.FutureTemporalInput
import com.apollo.graphql.type.KalenderavtaleTilstand
import com.apollo.graphql.type.MottakerInput
import com.apollo.graphql.type.NaermesteLederMottakerInput
import com.apollographql.apollo.api.Optional
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
 * @param hardDeleteTidspunkt Når avtalen skal slettes.
 */
data class NyKalenderInput(
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
    val hardDeleteTidspunkt: LocalDateTime,
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
        tilstand = Optional.present(KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER),
        eksterneVarsler = listOf(),
        paaminnelse = Optional.absent(),
        hardDelete = Optional.present(
            FutureTemporalInput(
                den = Optional.present(
                    hardDeleteTidspunkt.formatAsISO8601DateTime()
                )
            )
        ),
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
    val nyTilstand: KalenderavtaleTilstand,
    val nyTekst: String,
    val nyLenke: String? = null,
    val hardDeleteTidspunkt: LocalDateTime,
)