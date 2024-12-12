package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import com.apollo.graphql.NyBeskjedMutation
import com.apollo.graphql.type.EksterntVarselEpostInput
import com.apollo.graphql.type.EksterntVarselInput
import com.apollo.graphql.type.EpostKontaktInfoInput
import com.apollo.graphql.type.EpostMottakerInput
import com.apollo.graphql.type.FutureTemporalInput
import com.apollo.graphql.type.MetadataInput
import com.apollo.graphql.type.MottakerInput
import com.apollo.graphql.type.NaermesteLederMottakerInput
import com.apollo.graphql.type.NotifikasjonInput
import com.apollo.graphql.type.NyBeskjedInput
import com.apollo.graphql.type.SendetidspunktInput
import com.apollo.graphql.type.Sendevindu
import com.apollographql.apollo.api.Optional
import no.nav.syfo.producer.arbeidsgivernotifikasjon.formatAsISO8601DateTime
import java.time.LocalDateTime

data class ArbeidsgiverNotifikasjon(
    val varselId: String,
    val virksomhetsnummer: String,
    val url: String,
    val narmesteLederFnr: String,
    val ansattFnr: String,
    val messageText: String,
    val narmesteLederEpostadresse: String,
    val merkelapp: String,
    val emailTitle: String,
    val emailBody: String,
    val hardDeleteDate: LocalDateTime?,
    val grupperingsid: String,
)

data class ArbeidsgiverDeleteNotifikasjon(
    val merkelapp: String,
    val eksternReferanse: String
)

fun ArbeidsgiverNotifikasjon.toNyBeskjedMutation(): NyBeskjedMutation {
    return NyBeskjedMutation(
        nyBeskjed = NyBeskjedInput(
            mottakere = Optional.present(
                listOf(
                    MottakerInput(
                        naermesteLeder = Optional.present(
                            NaermesteLederMottakerInput(
                                naermesteLederFnr = narmesteLederFnr,
                                ansattFnr = ansattFnr
                            )
                        )
                    )
                )
            ),
            notifikasjon = NotifikasjonInput(
                merkelapp = merkelapp,
                tekst = messageText,
                lenke = url
            ),
            metadata = MetadataInput(
                virksomhetsnummer = virksomhetsnummer,
                eksternId = varselId,
                grupperingsid = Optional.present(grupperingsid),
                hardDelete = hardDeleteDate?.let {
                    Optional.present(
                        FutureTemporalInput(
                            den = Optional.present(it.formatAsISO8601DateTime())
                        )
                    )
                } ?: Optional.Absent,
            ),
            eksterneVarsler = Optional.present(
                listOf(
                    EksterntVarselInput(
                        epost = Optional.presentIfNotNull(
                            EksterntVarselEpostInput(
                                mottaker = EpostMottakerInput(
                                    kontaktinfo = Optional.present(
                                        EpostKontaktInfoInput(
                                            epostadresse = narmesteLederEpostadresse
                                        )
                                    )
                                ),
                                epostTittel = emailBody,
                                epostHtmlBody = emailBody,
                                sendetidspunkt = SendetidspunktInput(
                                    tidspunkt = Optional.Absent,
                                    sendevindu = Optional.present(Sendevindu.NKS_AAPNINGSTID)
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}
