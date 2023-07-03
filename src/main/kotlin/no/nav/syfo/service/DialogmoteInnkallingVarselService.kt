package no.nav.syfo.service

import no.nav.syfo.*
import no.nav.syfo.kafka.consumers.varselbus.domain.*
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.*
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import org.apache.commons.cli.MissingArgumentException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class DialogmoteInnkallingVarselService(
    val senderFacade: SenderFacade,
    val dialogmoterUrl: String,
    val accessControlService: AccessControlService,
) {
    val WEEKS_BEFORE_DELETE = 4L
    val SMS_KEY = "smsText"
    val EMAIL_TITLE_KEY = "emailTitle"
    val EMAIL_BODY_KEY = "emailBody"
    private val log = LoggerFactory.getLogger(DialogmoteInnkallingVarselService::class.qualifiedName)

    fun sendVarselTilNarmesteLeder(varselHendelse: NarmesteLederHendelse) {
        log.info("[DIALOGMOTE_STATUS_VARSEL_SERVICE]: sender dialogmote hendelse til narmeste leder ${varselHendelse.type}")
        varselHendelse.data = dataToVarselDataNarmesteLeder(varselHendelse.data)
        sendVarselTilDineSykmeldte(varselHendelse)
        sendVarselTilArbeidsgiverNotifikasjon(varselHendelse)
    }

    fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        val jounalpostData = dataToVarselDataJournalpost(varselHendelse.data)
        val varselUuid = jounalpostData.uuid
        val arbeidstakerFnr = varselHendelse.arbeidstakerFnr
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerFnr)
        val tekst = getArbeidstakerVarselText(varselHendelse.type)

        if (userAccessStatus.canUserBeDigitallyNotified) {
            varsleArbeidstakerViaBrukernotifkasjoner(varselHendelse, varselUuid, tekst, eksternVarsling = true)
        } else if (userAccessStatus.canUserBePhysicallyNotified) {
            val journalpostId = jounalpostData.id
            journalpostId?.let {
                sendFysiskBrevTilArbeidstaker(varselUuid, varselHendelse, journalpostId)
            } ?: log.info("Received journalpostId is null for user reserved from digital communication and with no addressebeskyttelse")
        } else {
            varsleArbeidstakerViaBrukernotifkasjoner(varselHendelse, varselUuid, tekst, eksternVarsling = false)
        }
    }

    fun getVarselUrl(varselHendelse: ArbeidstakerHendelse, varselUuid: String): URL {
        if (SM_DIALOGMOTE_REFERAT === varselHendelse.type) {
            return URL("$dialogmoterUrl/sykmeldt/referat/$varselUuid")
        }
        return URL("$dialogmoterUrl/sykmeldt/moteinnkalling")
    }

    private fun varsleArbeidstakerViaBrukernotifkasjoner(
        varselHendelse: ArbeidstakerHendelse,
        varselUuid: String,
        tekst: String,
        eksternVarsling: Boolean,
    ) {
        val url = getVarselUrl(varselHendelse, varselUuid)
        val meldingType = getMeldingTypeForSykmeldtVarsling(varselHendelse.type)
        val arbeidstakerFnr = varselHendelse.arbeidstakerFnr
        senderFacade.sendTilBrukernotifikasjoner(
            varselUuid, arbeidstakerFnr, tekst, url, varselHendelse, meldingType, eksternVarsling
        )
    }

    private fun sendFysiskBrevTilArbeidstaker(
        uuid: String,
        arbeidstakerHendelse: ArbeidstakerHendelse,
        journalpostId: String,
    ) {
        try {
            senderFacade.sendBrevTilFysiskPrint(uuid, arbeidstakerHendelse, journalpostId)
        } catch (e: RuntimeException) {
            log.info("Feil i sending av fysisk brev om dialogmote: ${e.message} for hendelsetype: ${arbeidstakerHendelse.type.name}")
        }
    }

    private fun sendVarselTilArbeidsgiverNotifikasjon(varselHendelse: NarmesteLederHendelse) {
        val uuid = UUID.randomUUID()
        val orgnummer = varselHendelse.orgnummer
        val narmesteLederFnr = varselHendelse.narmesteLederFnr
        val arbeidstakerFnr = varselHendelse.arbeidstakerFnr
        val texts = getArbeisgiverTexts(varselHendelse)
        val sms = texts[SMS_KEY]
        val emailTitle = texts[EMAIL_TITLE_KEY]
        val emailBody = texts[EMAIL_BODY_KEY]
        val hardDeleteDate = LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)

        if (!sms.isNullOrBlank() && !emailTitle.isNullOrBlank() && !emailBody.isNullOrBlank()) {
            val input = ArbeidsgiverNotifikasjonInput(
                uuid,
                orgnummer,
                narmesteLederFnr,
                arbeidstakerFnr,
                ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
                sms,
                emailTitle,
                emailBody,
                hardDeleteDate,
            )

            senderFacade.sendTilArbeidsgiverNotifikasjon(
                varselHendelse,
                input,
            )
        } else {
            log.warn("Kunne ikke mappe tekstene til arbeidsgiver-tekst for dialogmote varsel av type: ${varselHendelse.type.name}")
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
                utlopstidspunkt = OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
            )
            senderFacade.sendTilDineSykmeldte(varselHendelse, dineSykmeldteVarsel)
        }
    }

    private fun getArbeidstakerVarselText(hendelseType: HendelseType): String {
        return when (hendelseType) {
            SM_DIALOGMOTE_INNKALT -> BRUKERNOTIFIKASJONER_DIALOGMOTE_INNKALT_TEKST
            SM_DIALOGMOTE_AVLYST -> BRUKERNOTIFIKASJONER_DIALOGMOTE_AVLYST_TEKST
            SM_DIALOGMOTE_NYTT_TID_STED -> BRUKERNOTIFIKASJONER_DIALOGMOTE_NYTT_TID_STED_TEKST
            SM_DIALOGMOTE_REFERAT -> BRUKERNOTIFIKASJONER_DIALOGMOTE_REFERAT_TEKST
            SM_DIALOGMOTE_LEST -> ""
            else -> {
                throw IllegalArgumentException("Kan ikke mappe $hendelseType til arbeidstaker varsel text")
            }
        }
    }

    private fun getDineSykmeldteVarselText(hendelseType: HendelseType): String {
        return when (hendelseType) {
            NL_DIALOGMOTE_INNKALT -> DINE_SYKMELDTE_DIALOGMOTE_INNKALT_TEKST
            NL_DIALOGMOTE_AVLYST -> DINE_SYKMELDTE_DIALOGMOTE_AVLYST_TEKST
            NL_DIALOGMOTE_NYTT_TID_STED -> DINE_SYKMELDTE_DIALOGMOTE_NYTT_TID_STED_TEKST
            NL_DIALOGMOTE_REFERAT -> DINE_SYKMELDTE_DIALOGMOTE_REFERAT_TEKST
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

            NL_DIALOGMOTE_NYTT_TID_STED -> hashMapOf(
                SMS_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_MESSAGE_TEXT,
                EMAIL_TITLE_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_NYTT_TID_STED_EMAIL_TITLE,
                EMAIL_BODY_KEY to getEmailBody(hendelse),
            )

            NL_DIALOGMOTE_REFERAT -> hashMapOf(
                SMS_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_MESSAGE_TEXT,
                EMAIL_TITLE_KEY to ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_REFERAT_EMAIL_TITLE,
                EMAIL_BODY_KEY to getEmailBody(hendelse),
            )

            else -> hashMapOf()
        }
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

    fun dataToVarselDataNarmesteLeder(data: Any?): VarselDataNarmesteLeder {
        return data?.let {
            val varselData = data.toVarselData()
            varselData.narmesteLeder
                ?: throw IOException("VarselDataNarmesteLeder har feil format")
        } ?: throw MissingArgumentException("EsyfovarselHendelse mangler 'data'-felt")
    }

    fun getMeldingTypeForSykmeldtVarsling(hendelseType: HendelseType): BrukernotifikasjonKafkaProducer.MeldingType {
        return when (hendelseType) {
            SM_DIALOGMOTE_INNKALT -> BrukernotifikasjonKafkaProducer.MeldingType.OPPGAVE
            SM_DIALOGMOTE_AVLYST -> BrukernotifikasjonKafkaProducer.MeldingType.BESKJED
            SM_DIALOGMOTE_NYTT_TID_STED -> BrukernotifikasjonKafkaProducer.MeldingType.OPPGAVE
            SM_DIALOGMOTE_REFERAT -> BrukernotifikasjonKafkaProducer.MeldingType.BESKJED
            SM_DIALOGMOTE_LEST -> BrukernotifikasjonKafkaProducer.MeldingType.DONE
            else -> {
                throw IllegalArgumentException("Kan ikke mappe $hendelseType")
            }
        }
    }

    fun dataToVarselDataJournalpost(data: Any?): VarselDataJournalpost {
        return data?.let {
            try {
                val varselData = data.toVarselData()
                val journalpostdata = varselData.journalpost
                return journalpostdata?.uuid?.let { journalpostdata }
                    ?: throw MissingArgumentException("EsyfovarselHendelse mangler 'varselUuid'-felt")
            } catch (e: IOException) {
                throw IOException("ArbeidstakerHendelse har feil format")
            }
        } ?: throw MissingArgumentException("EsyfovarselHendelse mangler 'data'-felt")
    }
}
