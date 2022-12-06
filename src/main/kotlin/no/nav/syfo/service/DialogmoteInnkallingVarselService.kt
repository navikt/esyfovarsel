package no.nav.syfo.service

import no.nav.syfo.*
import no.nav.syfo.kafka.consumers.varselbus.domain.*
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.*
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class DialogmoteInnkallingVarselService(val senderFacade: SenderFacade, val dialogmoterUrl: String) {
    val WEEKS_BEFORE_DELETE = 4L
    val SMS_KEY = "smsText"
    val EMAIL_TITLE_KEY = "emailTitle"
    val EMAIL_BODY_KEY = "emailBody"
    private val log = LoggerFactory.getLogger(DialogmoteInnkallingVarselService::class.qualifiedName)

    fun sendVarselTilNarmesteLeder(varselHendelse: NarmesteLederHendelse) {
        log.info("[DIALOGMOTE_STATUS_VARSEL_SERVICE]: sender dialogmote hendelse til narmeste leder ${varselHendelse.type}")
        sendVarselTilDineSykmeldte(varselHendelse)
        sendVarselTilArbeidsgiverNotifikasjon(varselHendelse)
    }

    fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        val url = URL(dialogmoterUrl + BRUKERNOTIFIKASJONER_DIALOGMOTE_SYKMELDT_URL)
        val text = getArbeidstakerVarselText(varselHendelse.type)
        if (text.isNotBlank()) {
            senderFacade.sendTilBrukernotifikasjoner(
                UUID.randomUUID().toString(),
                varselHendelse.arbeidstakerFnr,
                text,
                url,
                varselHendelse
            )
        }
    }

    private fun sendVarselTilArbeidsgiverNotifikasjon(varselHendelse: NarmesteLederHendelse) {
        val texts = getArbeisgiverTexts(varselHendelse)
        val sms = texts[SMS_KEY]
        val emailTitle = texts[EMAIL_TITLE_KEY]
        val emailBody = texts[EMAIL_BODY_KEY]

        if (!sms.isNullOrBlank() && !emailTitle.isNullOrBlank() && !emailBody.isNullOrBlank()) {
            val input = ArbeidsgiverNotifikasjonInput(
                UUID.randomUUID(),
                varselHendelse.orgnummer,
                varselHendelse.narmesteLederFnr,
                varselHendelse.arbeidstakerFnr,
                ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
                sms,
                emailTitle,
                emailBody,
                LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)
            )

            senderFacade.sendTilArbeidsgiverNotifikasjon(
                varselHendelse,
                input
            )
        }
    }

    private fun sendVarselTilDineSykmeldte(varselHendelse: NarmesteLederHendelse) {
        val varselText = getDineSykmeldteVarselText(varselHendelse.type)
        if (varselText.isNotBlank()) {
            val dineSykmeldteVarsel = DineSykmeldteVarsel(
                ansattFnr = varselHendelse.arbeidstakerFnr,
                orgnr = varselHendelse.orgnummer,
                oppgavetype = varselHendelse.type.toDineSykmeldteHendelseType().toString(),
                lenke = null,
                tekst = varselText,
                utlopstidspunkt = OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)
            )
            senderFacade.sendTilDineSykmeldte(varselHendelse, dineSykmeldteVarsel)
        }
    }

    private fun getArbeidstakerVarselText(hendelseType: HendelseType): String {
        return when (hendelseType) {
            SM_DIALOGMOTE_INNKALT -> SM_DIALOGMOTE_INNKALT_TEKST
            SM_DIALOGMOTE_AVLYST -> SM_DIALOGMOTE_AVLYST_TEKST
            SM_DIALOGMOTE_NYTT_TID_STED -> SM_DIALOGMOTE_NYTT_TID_STED_TEKST
            SM_DIALOGMOTE_REFERAT -> SM_DIALOGMOTE_REFERAT_TEKST
            else -> {
                throw IllegalArgumentException("Kan ikke mappe $hendelseType til arbeidstaker varsel text")
            }
        }
    }

    private fun getDineSykmeldteVarselText(hendelseType: HendelseType): String {
        return when (hendelseType) {
            NL_DIALOGMOTE_INNKALT -> DINE_SYKMELDTE_DIALOGMOTE_INNKALT_TEKST
            NL_DIALOGMOTE_AVLYST -> DINE_SYKMELDTE_DIALOGMOTE_AVLYST_TEKST
            NL_DIALOGMOTE_REFERAT -> DINE_SYKMELDTE_DIALOGMOTE_NYTT_TID_STED_TEKST
            NL_DIALOGMOTE_NYTT_TID_STED -> DINE_SYKMELDTE_DIALOGMOTE_REFERAT_TEKST
            else -> {
                throw IllegalArgumentException("Kan ikke mappe $hendelseType til Dine sykmeldte varsel text")
            }
        }
    }

    private fun getArbeisgiverTexts(hendelse: NarmesteLederHendelse): HashMap<String, String> {
        return when (hendelse.type) {
            NL_DIALOGMOTE_INNKALT -> hashMapOf(
                SMS_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_MESSAGE_TEXT,
                EMAIL_TITLE_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_EMAIL_TITLE,
                EMAIL_BODY_KEY to getEmailBody(hendelse),
            )
            NL_DIALOGMOTE_AVLYST -> hashMapOf(
                SMS_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_MESSAGE_TEXT,
                EMAIL_TITLE_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_EMAIL_TITLE,
                EMAIL_BODY_KEY to getEmailBody(hendelse),
            )
            NL_DIALOGMOTE_REFERAT -> hashMapOf(
                SMS_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_MESSAGE_TEXT,
                EMAIL_TITLE_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_EMAIL_TITLE,
                EMAIL_BODY_KEY to getEmailBody(hendelse),
            )
            NL_DIALOGMOTE_NYTT_TID_STED -> hashMapOf(
                SMS_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_MESSAGE_TEXT,
                EMAIL_TITLE_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_EMAIL_TITLE,
                EMAIL_BODY_KEY to getEmailBody(hendelse),
            )
            else -> hashMapOf()
        }
    }

    private fun getEmailBody(hendelse: NarmesteLederHendelse): String {
        var greeting = "<body>Hei.<br><br>"

        val data = hendelse.data as DialogmoteInnkallingNarmesteLederData
        if (data.narmesteLederNavn.isNullOrBlank()) {
            greeting = "Til <body>${data.narmesteLederNavn},<br><br>"
        }

        return when (hendelse.type) {
            NL_DIALOGMOTE_INNKALT -> greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_INNKALT_EMAIL_BODY
            NL_DIALOGMOTE_AVLYST -> greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_AVLYST_EMAIL_BODY
            NL_DIALOGMOTE_REFERAT -> greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_EMAIL_BODY
            NL_DIALOGMOTE_NYTT_TID_STED -> greeting + ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_EMAIL_BODY
            else -> ""
        }
    }
}
