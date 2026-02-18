package no.nav.syfo.service

import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_EMAIL_BODY
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_EMAIL_TITLE
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_MESSAGE_TEXT
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_EMAIL_BODY
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_EMAIL_TITLE
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_MESSAGE_TEXT
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_EMAIL_BODY
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_EMAIL_TITLE
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_MESSAGE_TEXT
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_EMAIL_BODY
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_EMAIL_TITLE
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_MESSAGE_TEXT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_AVLYST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_INNKALT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_NYTT_TID_STED
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_REFERAT
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataNarmesteLeder
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import org.slf4j.LoggerFactory

/**
 * This class handles dialogmøte-changes "the old way" (without kalenderavtale and sak).
 * This is purely to handle the transition between the old way and the new way.
 * Should be deleted when no longer needed
 */
@Suppress("VariableNaming")
class DialogmoteInnkallingNarmestelederVarselServiceOLD(
    val senderFacade: SenderFacade,
) {
    val WEEKS_BEFORE_DELETE = 4L
    val SMS_KEY = "smsText"
    val EMAIL_TITLE_KEY = "emailTitle"
    val EMAIL_BODY_KEY = "emailBody"
    private val log = LoggerFactory.getLogger(DialogmoteInnkallingNarmestelederVarselServiceOLD::class.qualifiedName)

    suspend fun sendVarselTilNarmesteLeder(varselHendelse: NarmesteLederHendelse) {
        log.info("[Handle OLD varselutsending]: ${varselHendelse.type}")
        varselHendelse.data = dataToVarselDataNarmesteLeder(varselHendelse.data)
        sendVarselTilArbeidsgiverNotifikasjon(varselHendelse)
    }

    private suspend fun sendVarselTilArbeidsgiverNotifikasjon(varselHendelse: NarmesteLederHendelse) {
        val texts = getArbeisgiverTexts(varselHendelse)
        val sms = texts[SMS_KEY]
        val emailTitle = texts[EMAIL_TITLE_KEY]
        val emailBody = texts[EMAIL_BODY_KEY]

        if (!sms.isNullOrBlank() && !emailTitle.isNullOrBlank() && !emailBody.isNullOrBlank()) {
            val input =
                ArbeidsgiverNotifikasjonInput(
                    uuid = UUID.randomUUID(),
                    virksomhetsnummer = varselHendelse.orgnummer,
                    narmesteLederFnr = varselHendelse.narmesteLederFnr,
                    ansattFnr = varselHendelse.arbeidstakerFnr,
                    merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
                    messageText = sms,
                    epostTittel = emailTitle,
                    epostHtmlBody = emailBody,
                    hardDeleteDate = LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                    meldingstype = Meldingstype.OPPGAVE,
                    grupperingsid = UUID.randomUUID().toString(),
                )

            senderFacade.sendTilArbeidsgiverNotifikasjon(
                varselHendelse,
                input,
            )
        } else {
            log.warn(
                "Kunne ikke mappe tekstene til arbeidsgiver-tekst " +
                    "for dialogmote varsel av type: ${varselHendelse.type.name}",
            )
        }
    }

    private fun getArbeisgiverTexts(hendelse: NarmesteLederHendelse): HashMap<String, String> =
        when (hendelse.type) {
            NL_DIALOGMOTE_INNKALT ->
                hashMapOf(
                    SMS_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_MESSAGE_TEXT,
                    EMAIL_TITLE_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_EMAIL_TITLE,
                    EMAIL_BODY_KEY to getEmailBody(hendelse),
                )

            NL_DIALOGMOTE_AVLYST ->
                hashMapOf(
                    SMS_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_MESSAGE_TEXT,
                    EMAIL_TITLE_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_EMAIL_TITLE,
                    EMAIL_BODY_KEY to getEmailBody(hendelse),
                )

            NL_DIALOGMOTE_NYTT_TID_STED ->
                hashMapOf(
                    SMS_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_MESSAGE_TEXT,
                    EMAIL_TITLE_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_EMAIL_TITLE,
                    EMAIL_BODY_KEY to getEmailBody(hendelse),
                )

            NL_DIALOGMOTE_REFERAT ->
                hashMapOf(
                    SMS_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_MESSAGE_TEXT,
                    EMAIL_TITLE_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_EMAIL_TITLE,
                    EMAIL_BODY_KEY to getEmailBody(hendelse),
                )

            else -> hashMapOf()
        }

    private fun getEmailBody(hendelse: NarmesteLederHendelse): String {
        var greeting = "<body>Hei.<br><br>"

        val narmesteLeder = hendelse.data as VarselDataNarmesteLeder
        if (!narmesteLeder.navn.isNullOrBlank()) {
            greeting = "Til <body>${narmesteLeder.navn},<br><br>"
        }

        return when (hendelse.type) {
            NL_DIALOGMOTE_INNKALT -> greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_EMAIL_BODY
            NL_DIALOGMOTE_AVLYST -> greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_EMAIL_BODY
            NL_DIALOGMOTE_REFERAT -> greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_EMAIL_BODY
            NL_DIALOGMOTE_NYTT_TID_STED -> greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_EMAIL_BODY
            else -> ""
        }
    }

    fun dataToVarselDataNarmesteLeder(data: Any?): VarselDataNarmesteLeder =
        data?.let {
            val varselData = data.toVarselData()
            varselData.narmesteLeder
                ?: throw IOException("VarselDataNarmesteLeder har feil format")
        } ?: throw IllegalArgumentException("EsyfovarselHendelse mangler 'data'-felt")
}
