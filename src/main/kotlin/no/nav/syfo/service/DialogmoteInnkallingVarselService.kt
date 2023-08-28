package no.nav.syfo.service

import no.nav.syfo.*
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.kafka.consumers.varselbus.domain.*
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.*
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import org.apache.commons.cli.MissingArgumentException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.Serializable
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

enum class DittSykefravaerHendelsetypeDialogmoteInnkalling : Serializable {
    ESYFOVARSEL_DIALOGMOTE_INNKALT,
    ESYFOVARSEL_DIALOGMOTE_AVLYST,
    ESYFOVARSEL_DIALOGMOTE_REFERAT,
    ESYFOVARSEL_DIALOGMOTE_NYTT_TID_STED,
    ESYFOVARSEL_DIALOGMOTE_LEST,
}

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

        if (userAccessStatus.canUserBeDigitallyNotified) {
            varsleArbeidstakerViaBrukernotifkasjoner(varselHendelse, varselUuid, eksternVarsling = true)
        } else if (userAccessStatus.canUserBePhysicallyNotified) {
            val journalpostId = jounalpostData.id
            journalpostId?.let {
                sendFysiskBrevTilArbeidstaker(varselUuid, varselHendelse, journalpostId)
            } ?: log.info("Received journalpostId is null for user reserved from digital communication and with no addressebeskyttelse")
        } else {
            varsleArbeidstakerViaBrukernotifkasjoner(varselHendelse, varselUuid, eksternVarsling = false)
        }
        sendOppgaveTilDittSykefravaerOgFerdigstillTidligereMeldinger(varselHendelse, varselUuid)
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
        eksternVarsling: Boolean,
    ) {
        senderFacade.sendTilBrukernotifikasjoner(
            uuid = varselUuid,
            mottakerFnr = varselHendelse.arbeidstakerFnr,
            content = getArbeidstakerVarselText(varselHendelse.type),
            url = getVarselUrl(varselHendelse, varselUuid),
            varselHendelse = varselHendelse,
            meldingType = getMeldingTypeForSykmeldtVarsling(varselHendelse.type),
            eksternVarsling = eksternVarsling,
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
        val texts = getArbeisgiverTexts(varselHendelse)
        val sms = texts[SMS_KEY]
        val emailTitle = texts[EMAIL_TITLE_KEY]
        val emailBody = texts[EMAIL_BODY_KEY]

        if (!sms.isNullOrBlank() && !emailTitle.isNullOrBlank() && !emailBody.isNullOrBlank()) {
            val input = ArbeidsgiverNotifikasjonInput(
                uuid = UUID.randomUUID(),
                virksomhetsnummer = varselHendelse.orgnummer,
                narmesteLederFnr = varselHendelse.narmesteLederFnr,
                ansattFnr = varselHendelse.arbeidstakerFnr,
                merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
                messageText = sms,
                emailTitle = emailTitle,
                emailBody = emailBody,
                hardDeleteDate = LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                meldingstype = Meldingstype.OPPGAVE,
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

    fun getMessageText(arbeidstakerHendelse: ArbeidstakerHendelse): String? {
        return when (arbeidstakerHendelse.type) {
            SM_DIALOGMOTE_INNKALT -> DITT_SYKEFRAVAER_DIALOGMOTE_INNKALLING_MESSAGE_TEXT
            SM_DIALOGMOTE_AVLYST -> DITT_SYKEFRAVAER_DIALOGMOTE_AVLYSNING_MESSAGE_TEXT
            SM_DIALOGMOTE_REFERAT -> DITT_SYKEFRAVAER_DIALOGMOTE_REFERAT_MESSAGE_TEXT
            SM_DIALOGMOTE_NYTT_TID_STED -> DITT_SYKEFRAVAER_DIALOGMOTE_ENDRING_MESSAGE_TEXT
            SM_DIALOGMOTE_LEST -> ""
            else -> {
                log.error("Klarte ikke mappe varsel av type ${arbeidstakerHendelse.type} ved mapping hendelsetype til ditt sykefravar melding tekst")
                null
            }
        }
    }

    fun getDittSykefravarHendelseType(arbeidstakerHendelse: ArbeidstakerHendelse): String? {
        return when (arbeidstakerHendelse.type) {
            SM_DIALOGMOTE_INNKALT -> DittSykefravaerHendelsetypeDialogmoteInnkalling.ESYFOVARSEL_DIALOGMOTE_INNKALT.toString()
            SM_DIALOGMOTE_AVLYST -> DittSykefravaerHendelsetypeDialogmoteInnkalling.ESYFOVARSEL_DIALOGMOTE_AVLYST.toString()
            SM_DIALOGMOTE_REFERAT -> DittSykefravaerHendelsetypeDialogmoteInnkalling.ESYFOVARSEL_DIALOGMOTE_REFERAT.toString()
            SM_DIALOGMOTE_NYTT_TID_STED -> DittSykefravaerHendelsetypeDialogmoteInnkalling.ESYFOVARSEL_DIALOGMOTE_NYTT_TID_STED.toString()
            SM_DIALOGMOTE_LEST -> DittSykefravaerHendelsetypeDialogmoteInnkalling.ESYFOVARSEL_DIALOGMOTE_LEST.toString()
            else -> {
                log.error("Klarte ikke mappe varsel av type ${arbeidstakerHendelse.type} ved mapping hendelsetype til ditt sykefravar hendelsetype")
                null
            }
        }
    }

    private fun sendOppgaveTilDittSykefravaerOgFerdigstillTidligereMeldinger(
        arbeidstakerHendelse: ArbeidstakerHendelse,
        varselUuid: String,
    ) {
        val utsendteVarsler = senderFacade.fetchAlleUferdigstilteVarslerTilKanal(
            arbeidstakerHendelse.arbeidstakerFnr,
            Kanal.DITT_SYKEFRAVAER,
        )
        val melding = opprettDittSykefravaerMelding(arbeidstakerHendelse, varselUuid)

        if (utsendteVarsler.isNotEmpty() &&
            listOf(SM_DIALOGMOTE_INNKALT, SM_DIALOGMOTE_NYTT_TID_STED, SM_DIALOGMOTE_AVLYST, SM_DIALOGMOTE_REFERAT)
                .contains(arbeidstakerHendelse.type)
        ) {
            senderFacade.ferdigstillDittSykefravaerVarsler(arbeidstakerHendelse) // ferdigstille seg selv
            if (arbeidstakerHendelse.type == SM_DIALOGMOTE_INNKALT) {
                senderFacade.ferdigstillDittSykefravaerVarslerAvTyper(
                    arbeidstakerHendelse,
                    setOf(SM_DIALOGMOTE_NYTT_TID_STED),
                )
            }
            if (arbeidstakerHendelse.type == SM_DIALOGMOTE_NYTT_TID_STED) {
                senderFacade.ferdigstillDittSykefravaerVarslerAvTyper(
                    arbeidstakerHendelse,
                    setOf(SM_DIALOGMOTE_INNKALT),
                )
            } else {
                senderFacade.ferdigstillDittSykefravaerVarslerAvTyper(
                    arbeidstakerHendelse,
                    setOf(SM_DIALOGMOTE_INNKALT, SM_DIALOGMOTE_NYTT_TID_STED),
                )
            }
        }

        if (melding != null) {
            senderFacade.sendTilDittSykefravaer(
                arbeidstakerHendelse,
                DittSykefravaerVarsel(
                    varselUuid,
                    melding,
                ),
            )
        }
        if (arbeidstakerHendelse.type == SM_DIALOGMOTE_LEST) {
            senderFacade.ferdigstillDittSykefravaerVarsler(arbeidstakerHendelse)
        }
    }

    fun opprettDittSykefravaerMelding(
        arbeidstakerHendelse: ArbeidstakerHendelse,
        varselUuid: String,
    ): DittSykefravaerMelding? {
        val messageText = getMessageText(arbeidstakerHendelse)
        val hendelseType = getDittSykefravarHendelseType(arbeidstakerHendelse)

        if (messageText != null && hendelseType != null && hendelseType != SM_DIALOGMOTE_LEST.name) {
            return DittSykefravaerMelding(
                OpprettMelding(
                    messageText,
                    getVarselUrl(arbeidstakerHendelse, varselUuid).toString(),
                    Variant.INFO,
                    true,
                    hendelseType,
                    OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE).toInstant(),
                ),
                null,
                arbeidstakerHendelse.arbeidstakerFnr,
            )
        } else {
            return null
        }
    }
}
