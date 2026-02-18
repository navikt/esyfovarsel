package no.nav.syfo

import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_AVLYST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_INNKALT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_NYTT_TID_STED
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_REFERAT
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData

data class DialogmoteNarmesteLederTexts(
    val messageText: String,
    val epostTittel: String,
    val emailBody: String,
    val dineSykmeldteText: String,
)

fun NarmesteLederHendelse.dialogmoteNarmesteLederTexts(): DialogmoteNarmesteLederTexts {
    val greeting =
        this.data
            ?.toVarselData()
            ?.narmesteLeder
            ?.navn
            ?.let { navn -> "Til <body>$navn,<br><br>" }
            ?: "<body>Hei.<br><br>"
    return when (this.type) {
        NL_DIALOGMOTE_INNKALT ->
            DialogmoteNarmesteLederTexts(
                messageText = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_MESSAGE_TEXT,
                epostTittel = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_EMAIL_TITLE,
                emailBody = greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_EMAIL_BODY,
                dineSykmeldteText = DINE_SYKMELDTE_DIALOGMOTE_INNKALT_TEKST,
            )
        NL_DIALOGMOTE_AVLYST ->
            DialogmoteNarmesteLederTexts(
                messageText = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_MESSAGE_TEXT,
                epostTittel = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_EMAIL_TITLE,
                emailBody = greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_EMAIL_BODY,
                dineSykmeldteText = DINE_SYKMELDTE_DIALOGMOTE_AVLYST_TEKST,
            )
        NL_DIALOGMOTE_NYTT_TID_STED ->
            DialogmoteNarmesteLederTexts(
                messageText = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_MESSAGE_TEXT,
                epostTittel = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_EMAIL_TITLE,
                emailBody = greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_EMAIL_BODY,
                dineSykmeldteText = DINE_SYKMELDTE_DIALOGMOTE_NYTT_TID_STED_TEKST,
            )
        NL_DIALOGMOTE_REFERAT ->
            DialogmoteNarmesteLederTexts(
                messageText = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_MESSAGE_TEXT,
                epostTittel = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_EMAIL_TITLE,
                emailBody = greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_EMAIL_BODY,
                dineSykmeldteText = DINE_SYKMELDTE_DIALOGMOTE_REFERAT_TEKST,
            )
        else -> throw IllegalArgumentException("Unsupported type: ${this.type}")
    }
}
