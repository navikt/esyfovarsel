package no.nav.syfo.service

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
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_AVLYST_TEKST
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_INNKALT_TEKST
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_NYTT_TID_STED_TEKST
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_REFERAT_TEKST
import no.nav.syfo.DINE_SYKMELDTE_DIALOGMOTE_AVLYST_TEKST
import no.nav.syfo.DINE_SYKMELDTE_DIALOGMOTE_INNKALT_TEKST
import no.nav.syfo.DINE_SYKMELDTE_DIALOGMOTE_NYTT_TID_STED_TEKST
import no.nav.syfo.DINE_SYKMELDTE_DIALOGMOTE_REFERAT_TEKST
import no.nav.syfo.DITT_SYKEFRAVAER_DIALOGMOTE_AVLYSNING_MESSAGE_TEXT
import no.nav.syfo.DITT_SYKEFRAVAER_DIALOGMOTE_ENDRING_MESSAGE_TEXT
import no.nav.syfo.DITT_SYKEFRAVAER_DIALOGMOTE_INNKALLING_MESSAGE_TEXT
import no.nav.syfo.DITT_SYKEFRAVAER_DIALOGMOTE_REFERAT_MESSAGE_TEXT
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.pdl.HentPersonData
import no.nav.syfo.consumer.pdl.PdlClient
import no.nav.syfo.consumer.pdl.firstName
import no.nav.syfo.consumer.pdl.fullName
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.PKalenderInput
import no.nav.syfo.db.getArbeidsgivernotifikasjonerKalenderavtale
import no.nav.syfo.db.getArbeidsgivernotifikasjonerSak
import no.nav.syfo.db.storeArbeidsgivernotifikasjonerKalenderavtale
import no.nav.syfo.db.storeArbeidsgivernotifikasjonerSak
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_AVLYST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_INNKALT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_NYTT_TID_STED
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_REFERAT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_AVLYST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_INNKALT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_LEST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_NYTT_TID_STED
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_REFERAT
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataJournalpost
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataNarmesteLeder
import no.nav.syfo.kafka.consumers.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.KalenderTilstand
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyKalenderInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.OppdaterKalenderInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.SakStatus
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.toPKalenderInput
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType
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
    val narmesteLederService: NarmesteLederService,
    val pdlClient: PdlClient,
    val database: DatabaseInterface,
) {
    val WEEKS_BEFORE_DELETE = 4L
    val SMS_KEY = "smsText"
    val EMAIL_TITLE_KEY = "emailTitle"
    val EMAIL_BODY_KEY = "emailBody"
    private val log = LoggerFactory.getLogger(DialogmoteInnkallingVarselService::class.qualifiedName)

    suspend fun sendVarselTilNarmesteLeder(varselHendelse: NarmesteLederHendelse) {
        log.info("[DIALOGMOTE_STATUS_VARSEL_SERVICE]: sender dialogmote hendelse til narmeste leder ${varselHendelse.type}")
        varselHendelse.data = dataToVarselDataNarmesteLeder(varselHendelse.data)
        sendVarselTilDineSykmeldte(varselHendelse)

        val narmesteLederRelasjon = narmesteLederService.getNarmesteLederRelasjon(
            varselHendelse.arbeidstakerFnr, varselHendelse.orgnummer
        )

        if (narmesteLederRelasjon?.narmesteLederId == null) {
            log.warn("Sender ikke kalenderavtale: narmesteLederRelasjon er null, eller mangler narmesteLederId")
            return
        }

        val lenkeTilDialogmoteLanding = "$dialogmoterUrl/arbeidsgiver/${narmesteLederRelasjon.narmesteLederId}"
        val personData = pdlClient.hentPerson(varselHendelse.arbeidstakerFnr)

        val sakId = database.getArbeidsgivernotifikasjonerSak(
            narmesteLederRelasjon.narmesteLederId,
            ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP
        )?.sakId ?: createNewSak(
            varselHendelse,
            narmesteLederRelasjon.narmesteLederId,
            personData,
            lenkeTilDialogmoteLanding
        )

        sendVarselTilArbeidsgiverNotifikasjon(varselHendelse)
        sendKalenderAvtaleTilArbeidsgiverNotifikasjon(
            sakId = sakId,
            varselHendelse = varselHendelse,
            narmesteLederId = narmesteLederRelasjon.narmesteLederId,
            personData = personData,
            lenkeTilDialogmoteLanding = lenkeTilDialogmoteLanding
        )
    }

    suspend fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        val jounalpostData = dataToVarselDataJournalpost(varselHendelse.data)
        val varselUuid = jounalpostData.uuid
        val arbeidstakerFnr = varselHendelse.arbeidstakerFnr
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerFnr)

        if (userAccessStatus.canUserBeDigitallyNotified) {
            varsleArbeidstakerViaBrukernotifkasjoner(varselHendelse, varselUuid)
        } else {
            val journalpostId = jounalpostData.id
            journalpostId?.let {
                sendFysiskBrevTilArbeidstaker(varselUuid, varselHendelse, journalpostId)
            }
                ?: log.info("Skip sending fysisk brev to arbeidstaker: Jornalpostid is null")
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
    ) {
        senderFacade.sendTilBrukernotifikasjoner(
            uuid = varselUuid,
            mottakerFnr = varselHendelse.arbeidstakerFnr,
            content = getArbeidstakerVarselText(varselHendelse.type),
            url = getVarselUrl(varselHendelse, varselUuid),
            varselHendelse = varselHendelse,
            varseltype = getMeldingTypeForSykmeldtVarsling(varselHendelse.type),
            eksternVarsling = true,
        )
    }

    private suspend fun sendFysiskBrevTilArbeidstaker(
        uuid: String,
        arbeidstakerHendelse: ArbeidstakerHendelse,
        journalpostId: String,
    ) {
        try {
            senderFacade.sendBrevTilFysiskPrint(
                uuid = uuid,
                varselHendelse = arbeidstakerHendelse,
                journalpostId = journalpostId
            )
        } catch (e: RuntimeException) {
            log.info("Feil i sending av fysisk brev om dialogmote: ${e.message} for hendelsetype: ${arbeidstakerHendelse.type.name}")
        }
    }

    private suspend fun sendVarselTilArbeidsgiverNotifikasjon(varselHendelse: NarmesteLederHendelse) {
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

    private suspend fun createNewSak(
        varselHendelse: NarmesteLederHendelse,
        narmesteLederId: String,
        personData: HentPersonData?,
        lenkeTilDialogmoteLanding: String
    ): String {
        val sakInput = NySakInput(
            grupperingsid = narmesteLederId,
            merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
            virksomhetsnummer = varselHendelse.orgnummer,
            narmesteLederFnr = varselHendelse.narmesteLederFnr,
            ansattFnr = varselHendelse.arbeidstakerFnr,
            tittel = personData?.let { "Dialogmøte med ${it.fullName()}" } ?: "Innkalling til dialogmøte",
            lenke = lenkeTilDialogmoteLanding,
            initiellStatus = SakStatus.MOTTATT,
            tidspunkt = LocalDateTime.now().plusWeeks(1),
            hardDeleteDate = LocalDateTime.now().plusHours(5)
        )
        senderFacade.createNewSak(
            sakInput
        )

        return database.storeArbeidsgivernotifikasjonerSak(sakInput)
    }

    private suspend fun sendKalenderAvtaleTilArbeidsgiverNotifikasjon(
        sakId: String,
        varselHendelse: NarmesteLederHendelse,
        narmesteLederId: String,
        personData: HentPersonData?,
        lenkeTilDialogmoteLanding: String
    ) {
        val innkallingTekst = personData?.let { "Dialogmøte med ${it.firstName()}" } ?: "Innkalling til dialogmøte"
        val avlystTekst = personData?.let { "Dialogmøte med ${it.firstName()} er avlyst" } ?: "Dialogmøtet er avlyst"

        when (varselHendelse.type) {
            NL_DIALOGMOTE_INNKALT -> {
                createNewKalenderavtale(
                    sakId = sakId,
                    varselHendelse = varselHendelse,
                    narmesteLederId = narmesteLederId,
                    innkallingTekst = innkallingTekst,
                    lenkeTilDialogmoteLanding = lenkeTilDialogmoteLanding
                )
            }

            NL_DIALOGMOTE_NYTT_TID_STED -> {
                updateKalenderAvtale(
                    sakId = sakId,
                    avlystTekst = avlystTekst,
                    nyTilstand = KalenderTilstand.AVLYST,
                    hardDeleteDate = LocalDateTime.now()
                )

                createNewKalenderavtale(
                    sakId = sakId,
                    varselHendelse = varselHendelse,
                    narmesteLederId = narmesteLederId,
                    innkallingTekst = innkallingTekst,
                    lenkeTilDialogmoteLanding = lenkeTilDialogmoteLanding
                )
            }

            NL_DIALOGMOTE_AVLYST -> {
                updateKalenderAvtale(
                    sakId = sakId,
                    avlystTekst = avlystTekst,
                    nyTilstand = KalenderTilstand.AVLYST,
                    hardDeleteDate = LocalDateTime.now()
                )
            }

            else -> {
                log.warn("Ikke implementert kalenderavtale for type: ${varselHendelse.type.name}")
            }
        }
    }

    private suspend fun createNewKalenderavtale(
        sakId: String,
        varselHendelse: NarmesteLederHendelse,
        narmesteLederId: String,
        innkallingTekst: String,
        lenkeTilDialogmoteLanding: String
    ) {
        val kalenderInput = NyKalenderInput(
            sakId = sakId,
            virksomhetsnummer = varselHendelse.orgnummer,
            grupperingsid = narmesteLederId,
            merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
            eksternId = UUID.randomUUID().toString(),
            tekst = innkallingTekst,
            ansattFnr = varselHendelse.arbeidstakerFnr,
            narmesteLederFnr = varselHendelse.narmesteLederFnr,
            startTidspunkt = LocalDateTime.now().plusWeeks(1),
            sluttTidspunkt = null,
            lenke = lenkeTilDialogmoteLanding,
            kalenderavtaleTilstand = KalenderTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
            hardDeleteDate = LocalDateTime.now().plusHours(1),
        )
        val kalenderId = senderFacade.createNewKalenderavtale(
            kalenderInput
        )
        if (kalenderId != null) {
            log.info("Successfully created new kalenderavtale. Storing...")
            database.storeArbeidsgivernotifikasjonerKalenderavtale(kalenderInput.toPKalenderInput(kalenderId))
        }
    }

    private suspend fun updateKalenderAvtale(
        sakId: String,
        avlystTekst: String,
        nyTilstand: KalenderTilstand,
        hardDeleteDate: LocalDateTime
    ) {
        val storedKalenderAvtale = database.getArbeidsgivernotifikasjonerKalenderavtale(sakId)
        require(storedKalenderAvtale != null) { "Kalenderavtale not found for sakId: $sakId" }
        val kalenderId = senderFacade.updateKalenderavtale(
            OppdaterKalenderInput(
                id = storedKalenderAvtale.kalenderId,
                nyTilstand = nyTilstand,
                nyTekst = avlystTekst,
                hardDeleteTidspunkt = hardDeleteDate,
            )
        )
        require(kalenderId != null) { "Failed to update kalenderavtale" }
        database.storeArbeidsgivernotifikasjonerKalenderavtale(
            PKalenderInput(
                eksternId = storedKalenderAvtale.eksternId,
                sakId = sakId,
                kalenderId = kalenderId,
                tekst = avlystTekst,
                startTidspunkt = storedKalenderAvtale.startTidspunkt,
                sluttTidspunkt = storedKalenderAvtale.sluttTidspunkt,
                kalenderavtaleTilstand = nyTilstand,
                hardDeleteDate = hardDeleteDate
            )
        )
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
        } ?: throw IllegalArgumentException("EsyfovarselHendelse mangler 'data'-felt")
    }

    fun getMeldingTypeForSykmeldtVarsling(hendelseType: HendelseType): InternalBrukernotifikasjonType {
        return when (hendelseType) {
            SM_DIALOGMOTE_INNKALT -> InternalBrukernotifikasjonType.OPPGAVE
            SM_DIALOGMOTE_AVLYST -> InternalBrukernotifikasjonType.BESKJED
            SM_DIALOGMOTE_NYTT_TID_STED -> InternalBrukernotifikasjonType.OPPGAVE
            SM_DIALOGMOTE_REFERAT -> InternalBrukernotifikasjonType.BESKJED
            SM_DIALOGMOTE_LEST -> InternalBrukernotifikasjonType.DONE
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
                    ?: throw IllegalArgumentException("EsyfovarselHendelse mangler 'varselUuid'-felt")
            } catch (e: IOException) {
                throw IOException("ArbeidstakerHendelse har feil format")
            }
        } ?: throw IllegalArgumentException("EsyfovarselHendelse mangler 'data'-felt")
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

    private suspend fun sendOppgaveTilDittSykefravaerOgFerdigstillTidligereMeldinger(
        arbeidstakerHendelse: ArbeidstakerHendelse,
        varselUuid: String,
    ) {
        val utsendteVarsler = senderFacade.fetchUferdigstilteVarsler(
            arbeidstakerFnr = PersonIdent(arbeidstakerHendelse.arbeidstakerFnr),
            kanal = Kanal.DITT_SYKEFRAVAER,
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

        if (messageText != null && hendelseType != null && hendelseType != DittSykefravaerHendelsetypeDialogmoteInnkalling.ESYFOVARSEL_DIALOGMOTE_LEST.name) {
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
