package no.nav.syfo.producer.arbeidsgivernotifikasjon.domain

import com.apollo.graphql.NyBeskjedMutation
import com.apollo.graphql.type.AltinnRessursMottakerInput
import com.apollo.graphql.type.EksterntVarselAltinnressursInput
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
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.metrics.countArbeidsgiverNotifikasjonMessageTextTruncated
import no.nav.syfo.producer.arbeidsgivernotifikasjon.AltinnRessursVariablesCreate
import no.nav.syfo.producer.arbeidsgivernotifikasjon.EpostSendevinduTypes
import no.nav.syfo.producer.arbeidsgivernotifikasjon.NarmestelederVariablesCreate
import no.nav.syfo.producer.arbeidsgivernotifikasjon.Variables
import no.nav.syfo.producer.arbeidsgivernotifikasjon.formatAsISO8601DateTime
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

sealed class ArbeidsgiverNotifikasjon {
    abstract val varselId: String
    abstract val virksomhetsnummer: String
    abstract val url: String
    abstract val messageText: String
    abstract val merkelapp: String
    abstract val emailTitle: String
    abstract val emailBody: String
    abstract val hardDeleteDate: LocalDateTime?
    abstract val grupperingsid: String

    abstract fun createVariables(): Variables

    protected fun sanitizedMessageText(mutationType: MutationType): String {
        if (messageText.length <= MAX_MESSAGE_TEXT_LENGTH) {
            return messageText
        }

        log.warn(
            "Truncating arbeidsgiver notifikasjon messageText over max length",
            keyValue("varselId", varselId),
            keyValue("mutationType", mutationType.name.lowercase()),
            keyValue("messageTextLength", messageText.length),
            keyValue("maxMessageTextLength", MAX_MESSAGE_TEXT_LENGTH),
        )
        countArbeidsgiverNotifikasjonMessageTextTruncated()
        return messageText.take(MAX_MESSAGE_TEXT_LENGTH)
    }

    fun toNyBeskjedMutation(): NyBeskjedMutation =
        NyBeskjedMutation(
            nyBeskjed =
                NyBeskjedInput(
                    mottakere = Optional.present(createMottakere()),
                    notifikasjon = createNotifikasjon(),
                    metadata = createMetadata(),
                    eksterneVarsler = Optional.present(createEksterneVarsler()),
                ),
        )

    protected abstract fun createMottakere(): List<MottakerInput>

    protected abstract fun createEksterneVarsler(): List<EksterntVarselInput>

    protected fun createSendetidspunkt(): SendetidspunktInput =
        SendetidspunktInput(
            tidspunkt = Optional.Absent,
            sendevindu = Optional.present(Sendevindu.NKS_AAPNINGSTID),
        )

    private fun createNotifikasjon(): NotifikasjonInput =
        NotifikasjonInput(
            merkelapp = merkelapp,
            tekst = sanitizedMessageText(MutationType.BESKJED),
            lenke = url,
        )

    private fun createMetadata(): MetadataInput =
        MetadataInput(
            virksomhetsnummer = virksomhetsnummer,
            eksternId = varselId,
            grupperingsid = Optional.present(grupperingsid),
            hardDelete =
                Optional.present(
                    FutureTemporalInput(
                        den = Optional.present(hardDeleteDate?.formatAsISO8601DateTime()),
                    ),
                ),
        )

    protected enum class MutationType {
        BESKJED,
        OPPGAVE,
    }

    companion object {
        private const val MAX_MESSAGE_TEXT_LENGTH = 300
        private val log = LoggerFactory.getLogger(ArbeidsgiverNotifikasjon::class.qualifiedName)
    }
}

data class ArbeidsgiverNotifikasjonNarmesteLeder(
    override val varselId: String,
    override val virksomhetsnummer: String,
    override val url: String,
    val narmesteLederFnr: String,
    val ansattFnr: String,
    override val messageText: String,
    val narmesteLederEpostadresse: String,
    override val merkelapp: String,
    override val emailTitle: String,
    override val emailBody: String,
    override val hardDeleteDate: LocalDateTime?,
    override val grupperingsid: String,
) : ArbeidsgiverNotifikasjon() {
    override fun createMottakere(): List<MottakerInput> =
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

    override fun createEksterneVarsler(): List<EksterntVarselInput> =
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
                                                epostadresse = narmesteLederEpostadresse,
                                            ),
                                        ),
                                ),
                            epostTittel = emailTitle,
                            epostHtmlBody = emailBody,
                            sendetidspunkt = createSendetidspunkt(),
                        ),
                    ),
            ),
        )

    override fun createVariables() =
        NarmestelederVariablesCreate(
            varselId,
            virksomhetsnummer,
            url,
            narmesteLederFnr,
            ansattFnr,
            merkelapp,
            sanitizedMessageText(MutationType.OPPGAVE),
            narmesteLederEpostadresse,
            emailTitle,
            emailBody,
            EpostSendevinduTypes.LOEPENDE,
            hardDeleteDate?.formatAsISO8601DateTime(),
            grupperingsid,
        )
}

data class ArbeidsgiverNotifikasjonAltinnRessurs(
    override val varselId: String,
    override val virksomhetsnummer: String,
    override val url: String,
    override val messageText: String,
    override val merkelapp: String,
    override val emailTitle: String,
    override val emailBody: String,
    val smsTekst: String,
    override val hardDeleteDate: LocalDateTime?,
    override val grupperingsid: String,
    val ressursId: String,
) : ArbeidsgiverNotifikasjon() {
    override fun createMottakere(): List<MottakerInput> =
        listOf(
            MottakerInput(
                altinnRessurs =
                    Optional.present(
                        AltinnRessursMottakerInput(
                            ressursId = ressursId,
                        ),
                    ),
            ),
        )

    override fun createEksterneVarsler(): List<EksterntVarselInput> =
        listOf(
            EksterntVarselInput(
                altinnressurs =
                    Optional.present(
                        EksterntVarselAltinnressursInput(
                            mottaker =
                                AltinnRessursMottakerInput(
                                    ressursId = ressursId,
                                ),
                            epostTittel = emailTitle,
                            epostHtmlBody = emailBody,
                            smsTekst = smsTekst,
                            sendetidspunkt = createSendetidspunkt(),
                        ),
                    ),
            ),
        )

    override fun createVariables() =
        AltinnRessursVariablesCreate(
            eksternId = varselId,
            virksomhetsnummer = virksomhetsnummer,
            lenke = url,
            merkelapp = merkelapp,
            tekst = sanitizedMessageText(MutationType.OPPGAVE),
            epostTittel = emailTitle,
            epostHtmlBody = emailBody,
            sendevindu = EpostSendevinduTypes.LOEPENDE,
            hardDeleteDate = hardDeleteDate?.formatAsISO8601DateTime(),
            grupperingsid = grupperingsid,
            ressursId = ressursId,
        )
}

data class ArbeidsgiverDeleteNotifikasjon(
    val merkelapp: String,
    val eksternReferanse: String,
)
