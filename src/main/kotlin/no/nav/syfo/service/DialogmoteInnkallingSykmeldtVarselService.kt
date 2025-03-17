package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_AVLYST_TEKST
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_INNKALT_TEKST
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_NYTT_TID_STED_TEKST
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_REFERAT_TEKST
import no.nav.syfo.DITT_SYKEFRAVAER_DIALOGMOTE_AVLYSNING_MESSAGE_TEXT
import no.nav.syfo.DITT_SYKEFRAVAER_DIALOGMOTE_ENDRING_MESSAGE_TEXT
import no.nav.syfo.DITT_SYKEFRAVAER_DIALOGMOTE_INNKALLING_MESSAGE_TEXT
import no.nav.syfo.DITT_SYKEFRAVAER_DIALOGMOTE_REFERAT_MESSAGE_TEXT
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_AVLYST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_INNKALT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_LEST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_NYTT_TID_STED
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_DIALOGMOTE_REFERAT
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataJournalpost
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.Serializable
import java.net.URI
import java.net.URL
import java.time.OffsetDateTime
import java.util.*

enum class DittSykefravaerHendelsetypeDialogmoteInnkalling : Serializable {
    ESYFOVARSEL_DIALOGMOTE_INNKALT,
    ESYFOVARSEL_DIALOGMOTE_AVLYST,
    ESYFOVARSEL_DIALOGMOTE_REFERAT,
    ESYFOVARSEL_DIALOGMOTE_NYTT_TID_STED,
    ESYFOVARSEL_DIALOGMOTE_LEST,
}

class DialogmoteInnkallingSykmeldtVarselService(
    val senderFacade: SenderFacade,
    val dialogmoterUrl: String,
    val accessControlService: AccessControlService,
    val database: DatabaseInterface,
) {
    val WEEKS_BEFORE_DELETE = 4L
    private val log = LoggerFactory.getLogger(DialogmoteInnkallingSykmeldtVarselService::class.qualifiedName)

    suspend fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        val jounalpostData = dataToVarselDataJournalpost(varselHendelse.data)
        val varselUuid = jounalpostData.uuid
        val arbeidstakerFnr = varselHendelse.arbeidstakerFnr
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerFnr)

        if (userAccessStatus.canUserBeDigitallyNotified) {
            varsleArbeidstakerViaBrukernotifikasjoner(varselHendelse, varselUuid)
        } else {
            val journalpostId = jounalpostData.id
            journalpostId?.let {
                sendFysiskBrevTilArbeidstaker(varselUuid, varselHendelse, journalpostId)
            }
                ?: log.info("Skip sending fysisk brev to arbeidstaker: Jornalpostid is null")
        }
        sendOppgaveTilDittSykefravaerOgFerdigstillTidligereMeldinger(varselHendelse, varselUuid)
    }

    fun getVarselUrl(hendelseType: HendelseType, varselUuid: String): URL {
        val urlString = if (SM_DIALOGMOTE_REFERAT === hendelseType) {
            "$dialogmoterUrl/sykmeldt/referat/$varselUuid"
        } else {
            "$dialogmoterUrl/sykmeldt/moteinnkalling"
        }
        return URI(urlString).toURL()
    }

    private fun varsleArbeidstakerViaBrukernotifikasjoner(
        varselHendelse: ArbeidstakerHendelse,
        varselUuid: String,
    ) {
        senderFacade.sendTilBrukernotifikasjoner(
            uuid = varselUuid,
            mottakerFnr = varselHendelse.arbeidstakerFnr,
            content = getArbeidstakerVarselText(varselHendelse.type),
            url = getVarselUrl(varselHendelse.type, varselUuid),
            arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
            orgnummer = varselHendelse.orgnummer,
            hendelseType = varselHendelse.type.name,
            varseltype = getMeldingTypeForSykmeldtVarsling(varselHendelse.type),
            eksternVarsling = true,
        )
    }

    fun revarsleArbeidstakerViaBrukernotifikasjoner(
        utsendtvarselFeilet: PUtsendtVarselFeilet,
    ): Boolean {
        val varselUuid = utsendtvarselFeilet.uuidEksternReferanse ?: UUID.randomUUID().toString()
        val hendelseType = HendelseType.valueOf(utsendtvarselFeilet.hendelsetypeNavn)

        return senderFacade.sendTilBrukernotifikasjoner(
            uuid = varselUuid,
            mottakerFnr = utsendtvarselFeilet.arbeidstakerFnr,
            content = getArbeidstakerVarselText(hendelseType),
            url = getVarselUrl(hendelseType, varselUuid),
            arbeidstakerFnr = utsendtvarselFeilet.arbeidstakerFnr,
            orgnummer = utsendtvarselFeilet.orgnummer,
            hendelseType = hendelseType.name,
            varseltype = getMeldingTypeForSykmeldtVarsling(hendelseType),
            eksternVarsling = true,
            storeFailedUtsending = false,
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
            log.info(
                "Feil i sending av fysisk brev om dialogmote: ${e.message} for hendelsetype: ${arbeidstakerHendelse.type.name}"
            )
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
                log.error(
                    "Klarte ikke mappe varsel av type ${arbeidstakerHendelse.type} ved mapping hendelsetype til ditt sykefravar melding tekst"
                )
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
                log.error(
                    "Klarte ikke mappe varsel av type ${arbeidstakerHendelse.type} ved mapping hendelsetype til ditt sykefravar hendelsetype"
                )
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
                    getVarselUrl(arbeidstakerHendelse.type, varselUuid).toString(),
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
