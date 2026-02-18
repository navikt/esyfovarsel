package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import com.apollo.graphql.NyKalenderavtaleMutation
import com.apollo.graphql.OppdaterKalenderavtaleMutation
import com.apollo.graphql.type.EksterntVarselEpostInput
import com.apollo.graphql.type.EksterntVarselInput
import com.apollo.graphql.type.EpostKontaktInfoInput
import com.apollo.graphql.type.EpostMottakerInput
import com.apollo.graphql.type.FutureTemporalInput
import com.apollo.graphql.type.HardDeleteUpdateInput
import com.apollo.graphql.type.KalenderavtaleTilstand
import com.apollo.graphql.type.MottakerInput
import com.apollo.graphql.type.NaermesteLederMottakerInput
import com.apollo.graphql.type.NyTidStrategi
import com.apollo.graphql.type.SendetidspunktInput
import com.apollo.graphql.type.Sendevindu
import com.apollographql.apollo.api.Optional
import java.time.LocalDateTime
import no.nav.syfo.db.domain.PKalenderInput
import no.nav.syfo.kafka.consumers.varselbus.domain.DialogmoteSvarType
import no.nav.syfo.producer.arbeidsgivernotifikasjon.formatAsISO8601DateTime

data class NyKalenderInput(
    val sakId: String,
    val virksomhetsnummer: String,
    val grupperingsId: String,
    val merkelapp: String,
    val eksternId: String,
    val tekst: String,
    val ansattFnr: String,
    val lenke: String,
    val narmesteLederFnr: String,
    val startTidspunkt: LocalDateTime,
    val sluttTidspunkt: LocalDateTime? = null,
    val kalenderavtaleTilstand: KalenderTilstand,
    val hardDeleteDate: LocalDateTime? = null,
    val ledersEpost: String,
    val epostTittel: String,
    val epostHtmlBody: String,
)

fun NyKalenderInput.toNyKalenderavtaleMutation(): NyKalenderavtaleMutation =
    NyKalenderavtaleMutation(
        virksomhetsnummer = virksomhetsnummer,
        grupperingsid = grupperingsId,
        merkelapp = merkelapp,
        eksternId = eksternId,
        tekst = tekst,
        lenke = lenke,
        mottakere = createMottakere(),
        startTidspunkt = startTidspunkt.formatAsISO8601DateTime(),
        sluttTidspunkt = Optional.presentIfNotNull(sluttTidspunkt?.formatAsISO8601DateTime()),
        lokasjon = Optional.absent(),
        erDigitalt = Optional.absent(),
        tilstand = Optional.present(KalenderavtaleTilstand.valueOf(kalenderavtaleTilstand.name)),
        eksterneVarsler = createEksterneVarsler(),
        paaminnelse = Optional.absent(),
        hardDelete = createHardDelete(),
    )

private fun NyKalenderInput.createMottakere(): List<MottakerInput> =
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
    )

private fun NyKalenderInput.createEksterneVarsler(): List<EksterntVarselInput> =
    listOf(
        EksterntVarselInput(
            epost =
                Optional.presentIfNotNull(
                    EksterntVarselEpostInput(
                        mottaker =
                            EpostMottakerInput(
                                kontaktinfo =
                                    Optional.present(
                                        EpostKontaktInfoInput(
                                            epostadresse = ledersEpost,
                                        ),
                                    ),
                            ),
                        epostTittel = epostTittel,
                        epostHtmlBody = epostHtmlBody,
                        sendetidspunkt =
                            SendetidspunktInput(
                                tidspunkt = Optional.Absent,
                                sendevindu = Optional.present(Sendevindu.NKS_AAPNINGSTID),
                            ),
                    ),
                ),
        ),
    )

private fun NyKalenderInput.createHardDelete(): Optional<FutureTemporalInput> =
    hardDeleteDate?.let {
        Optional.present(
            FutureTemporalInput(
                den = Optional.present(it.formatAsISO8601DateTime()),
            ),
        )
    } ?: Optional.absent()

fun NyKalenderInput.toPKalenderInput(kalenderId: String): PKalenderInput =
    PKalenderInput(
        sakId = sakId,
        eksternId = eksternId,
        grupperingsid = grupperingsId,
        kalenderId = kalenderId,
        tekst = tekst,
        startTidspunkt = startTidspunkt,
        sluttTidspunkt = sluttTidspunkt,
        kalenderavtaleTilstand = kalenderavtaleTilstand,
        hardDeleteDate = hardDeleteDate,
        merkelapp = merkelapp,
    )

data class OppdaterKalenderInput(
    val id: String,
    val nyTilstand: KalenderTilstand? = null,
    val nyTekst: String? = null,
    val nyLenke: String? = null,
    val hardDeleteTidspunkt: LocalDateTime? = null,
    val ledersEpost: String? = null,
    val epostTittel: String? = null,
    val epostHtmlBody: String? = null,
)

fun OppdaterKalenderInput.toOppdaterKalenderavtaleMutation(): OppdaterKalenderavtaleMutation {
    val eksterneVarslerData =
        if (ledersEpost != null && epostTittel != null && epostHtmlBody != null) {
            listOf(
                EksterntVarselInput(
                    epost =
                        Optional.present(
                            EksterntVarselEpostInput(
                                mottaker =
                                    EpostMottakerInput(
                                        kontaktinfo =
                                            Optional.present(
                                                EpostKontaktInfoInput(
                                                    epostadresse = ledersEpost,
                                                ),
                                            ),
                                    ),
                                epostTittel = epostTittel,
                                epostHtmlBody = epostHtmlBody,
                                sendetidspunkt =
                                    SendetidspunktInput(
                                        tidspunkt = Optional.Absent,
                                        sendevindu = Optional.present(Sendevindu.NKS_AAPNINGSTID),
                                    ),
                            ),
                        ),
                ),
            )
        } else {
            emptyList()
        }

    return OppdaterKalenderavtaleMutation(
        id = id,
        eksterneVarsler = eksterneVarslerData,
        paaminnelse = Optional.absent(),
        nyTilstand = Optional.presentIfNotNull(nyTilstand?.let { KalenderavtaleTilstand.valueOf(it.name) }),
        nyLokasjon = Optional.absent(),
        nyLenke = Optional.presentIfNotNull(nyLenke),
        nyTekst = Optional.presentIfNotNull(nyTekst),
        nyErDigitalt = Optional.absent(),
        hardDelete =
            hardDeleteTidspunkt?.let {
                Optional.present(
                    HardDeleteUpdateInput(
                        nyTid =
                            FutureTemporalInput(
                                den = Optional.present(hardDeleteTidspunkt.formatAsISO8601DateTime()),
                            ),
                        strategi = NyTidStrategi.OVERSKRIV,
                    ),
                )
            } ?: Optional.absent(),
    )
}

enum class KalenderTilstand {
    VENTER_SVAR_FRA_ARBEIDSGIVER,
    ARBEIDSGIVER_VIL_AVLYSE,
    ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED,
    ARBEIDSGIVER_HAR_GODTATT,
    AVLYST,
    AVHOLDT,
}

fun DialogmoteSvarType.toKalenderTilstand(): KalenderTilstand =
    when (this) {
        DialogmoteSvarType.KOMMER -> KalenderTilstand.ARBEIDSGIVER_HAR_GODTATT
        DialogmoteSvarType.KOMMER_IKKE -> KalenderTilstand.ARBEIDSGIVER_VIL_AVLYSE
        DialogmoteSvarType.NYTT_TID_STED -> KalenderTilstand.ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED
    }
