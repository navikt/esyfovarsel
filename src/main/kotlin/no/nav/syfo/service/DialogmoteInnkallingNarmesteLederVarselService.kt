package no.nav.syfo.service

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.pdl.HentPersonData
import no.nav.syfo.consumer.pdl.PdlClient
import no.nav.syfo.consumer.pdl.firstName
import no.nav.syfo.consumer.pdl.fullName
import no.nav.syfo.db.domain.PSakInput
import no.nav.syfo.dialogmoteNarmesteLederTexts
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_AVLYST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_INNKALT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_NYTT_TID_STED
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_REFERAT
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_DIALOGMOTE_SVAR
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.getMotetidspunkt
import no.nav.syfo.kafka.consumers.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.KalenderTilstand
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyKalenderInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyStatusSakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.SakStatus
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.toKalenderTilstand
import org.slf4j.LoggerFactory

class DialogmoteInnkallingNarmesteLederVarselService(
    private val senderFacade: SenderFacade,
    private val dialogmoterUrl: String,
    private val narmesteLederService: NarmesteLederService,
    private val pdlClient: PdlClient,
) {
    val oldVarselService = DialogmoteInnkallingNarmestelederVarselServiceOLD(senderFacade)

    companion object {
        private const val WEEKS_BEFORE_DELETE = 4L
        private val log = LoggerFactory.getLogger(DialogmoteInnkallingNarmesteLederVarselService::class.qualifiedName)
    }

    suspend fun sendVarselTilNarmesteLeder(varselHendelse: NarmesteLederHendelse) {
        log.info("[DialogmoteInnkallingNarmesteLederVarselService]: sender ${varselHendelse.type}")
        val narmesteLederRelasjon =
            narmesteLederService.getNarmesteLederRelasjon(
                varselHendelse.arbeidstakerFnr,
                varselHendelse.orgnummer,
            )

        if (narmesteLederRelasjon?.narmesteLederId == null) {
            log.error("Sender ikke varsel: narmesteLederRelasjon er null, eller mangler narmesteLederId")
            return
        }

        if (narmesteLederRelasjon.narmesteLederEpost == null) {
            log.error("Sender ikke varsel: narmesteLederRelasjon mangler epost")
            return
        }

        val lenkeTilDialogmoteLanding = "$dialogmoterUrl/arbeidsgiver/${narmesteLederRelasjon.narmesteLederId}"
        val personData = pdlClient.hentPerson(personIdent = varselHendelse.arbeidstakerFnr)

        sendInfoTilNarmesteLeder(
            varselHendelse = varselHendelse,
            personData = personData,
            lenkeTilDialogmoteLanding = lenkeTilDialogmoteLanding,
            ledersEpost = narmesteLederRelasjon.narmesteLederEpost,
            narmesteLederId = narmesteLederRelasjon.narmesteLederId,
        )
    }

    private suspend fun sendInfoTilNarmesteLeder(
        varselHendelse: NarmesteLederHendelse,
        personData: HentPersonData?,
        lenkeTilDialogmoteLanding: String,
        ledersEpost: String,
        narmesteLederId: String,
    ) {
        when (varselHendelse.type) {
            NL_DIALOGMOTE_INNKALT ->
                handleInnkalt(
                    varselHendelse = varselHendelse,
                    personData = personData,
                    lenkeTilDialogmoteLanding = lenkeTilDialogmoteLanding,
                    ledersEpost = ledersEpost,
                    narmesteLederId = narmesteLederId,
                )

            NL_DIALOGMOTE_NYTT_TID_STED ->
                handleNyttTidSted(
                    varselHendelse = varselHendelse,
                    personData = personData,
                    lenkeTilDialogmoteLanding = lenkeTilDialogmoteLanding,
                    ledersEpost = ledersEpost,
                    narmesteLederId = narmesteLederId,
                )

            NL_DIALOGMOTE_AVLYST ->
                handleAvlyst(
                    varselHendelse = varselHendelse,
                    personData = personData,
                    ledersEpost = ledersEpost,
                    narmesteLederId = narmesteLederId,
                )

            NL_DIALOGMOTE_REFERAT ->
                handleReferat(
                    varselHendelse = varselHendelse,
                    personData = personData,
                    narmesteLederId = narmesteLederId,
                )

            NL_DIALOGMOTE_SVAR ->
                handleSvar(
                    varselHendelse = varselHendelse,
                    narmesteLederId = narmesteLederId,
                )

            else -> log.error("Forsøkte å sende nærmeste leder dialogmøtevarsel for type: ${varselHendelse.type.name}")
        }
    }

    private suspend fun handleInnkalt(
        varselHendelse: NarmesteLederHendelse,
        personData: HentPersonData?,
        lenkeTilDialogmoteLanding: String,
        ledersEpost: String,
        narmesteLederId: String,
    ) {
        sendVarselTilDineSykmeldte(varselHendelse)
        val varselData = varselHendelse.data?.toVarselData()
        require(varselData != null) { "DialogmoteInnkalt mangler varselData" }
        val motetidspunkt = varselData.getMotetidspunkt()
        val texts = varselHendelse.dialogmoteNarmesteLederTexts()
        val (sakId, grupperingsId) =
            createNewSak(
                narmestelederId = narmesteLederId,
                varselHendelse = varselHendelse,
                personData = personData,
                lenkeTilDialogmoteLanding = lenkeTilDialogmoteLanding,
                motetidspunkt = motetidspunkt,
            )
        createNewKalenderavtale(
            sakId = sakId,
            grupperingsId = grupperingsId,
            varselHendelse = varselHendelse,
            innkallingTekst = personData?.let { "Dialogmøte med ${it.firstName()}" } ?: "Innkalling til dialogmøte",
            lenkeTilDialogmoteLanding = lenkeTilDialogmoteLanding,
            ledersEpost = ledersEpost,
            epostTittel = texts.epostTittel,
            epostHtmlBody = texts.emailBody,
            motetidspunkt = motetidspunkt,
        )
    }

    private suspend fun handleNyttTidSted(
        varselHendelse: NarmesteLederHendelse,
        personData: HentPersonData?,
        lenkeTilDialogmoteLanding: String,
        ledersEpost: String,
        narmesteLederId: String,
    ) {
        sendVarselTilDineSykmeldte(varselHendelse)
        val sak = getPaagaaendeSak(narmesteLederId)
        if (sak == null) {
            log.warn("handleNyttTidSted: Fant ikke sak for narmesteLederId: $narmesteLederId. Sender på gammel måte")
            oldVarselService.sendVarselTilNarmesteLeder(varselHendelse)
            return
        }

        val varselData = varselHendelse.data?.toVarselData()
        require(varselData != null) { "DialogmoteInnkalt mangler varselData" }
        val motetidspunkt = varselData.getMotetidspunkt()
        val texts = varselHendelse.dialogmoteNarmesteLederTexts()
        senderFacade.updateKalenderavtale(
            sakId = sak.id,
            grupperingsId = sak.grupperingsid,
            nyTilstand = KalenderTilstand.AVLYST,
            nyTekst = personData?.let { "Dialogmøte med ${it.firstName()} er avlyst" } ?: "Dialogmøtet er avlyst",
            ledersEpost = ledersEpost,
            epostTittel = texts.epostTittel,
            epostHtmlBody = texts.emailBody,
        )
        createNewKalenderavtale(
            sakId = sak.id,
            grupperingsId = sak.grupperingsid,
            varselHendelse = varselHendelse,
            innkallingTekst =
                personData?.let { "Dialogmøte med ${it.firstName()} er flyttet" }
                    ?: "Dialogmøtet er flyttet",
            lenkeTilDialogmoteLanding = lenkeTilDialogmoteLanding,
            ledersEpost = ledersEpost,
            epostTittel = texts.epostTittel,
            epostHtmlBody = texts.emailBody,
            motetidspunkt = motetidspunkt,
        )
        updateSakStatus(
            sakId = sak.id,
            grupperingsId = sak.grupperingsid,
            nyStatus = SakStatus.MOTTATT,
            nyttMotetidspunkt = motetidspunkt,
        )
    }

    private suspend fun handleAvlyst(
        varselHendelse: NarmesteLederHendelse,
        personData: HentPersonData?,
        ledersEpost: String,
        narmesteLederId: String,
    ) {
        sendVarselTilDineSykmeldte(varselHendelse)
        val sak = getPaagaaendeSak(narmesteLederId)
        if (sak == null) {
            log.warn("handleAvlyst: Fant ikke sak for narmesteLederId: $narmesteLederId. Sender på gammel måte")
            oldVarselService.sendVarselTilNarmesteLeder(varselHendelse)
            return
        }
        val texts = varselHendelse.dialogmoteNarmesteLederTexts()
        senderFacade.updateKalenderavtale(
            sakId = sak.id,
            grupperingsId = sak.grupperingsid,
            nyTilstand = KalenderTilstand.AVLYST,
            nyTekst = personData?.let { "Dialogmøte med ${it.firstName()} er avlyst" } ?: "Dialogmøtet er avlyst",
            ledersEpost = ledersEpost,
            epostTittel = texts.epostTittel,
            epostHtmlBody = texts.emailBody,
        )
        updateSakStatus(sakId = sak.id, grupperingsId = sak.grupperingsid, nyStatus = SakStatus.FERDIG)
    }

    private suspend fun handleSvar(
        varselHendelse: NarmesteLederHendelse,
        narmesteLederId: String,
    ) {
        val sak = getPaagaaendeSak(narmesteLederId)
        if (sak == null) {
            log.warn("handleSvar: Fant ikke sak for narmesteLederId: $narmesteLederId. Skipper")
            return
        }
        val svar =
            varselHendelse.data
                ?.toVarselData()
                ?.dialogmoteSvar
                ?.svar
        require(svar != null) { "DialogmoteSvar-data mangler svar" }
        senderFacade.updateKalenderavtale(
            sakId = sak.id,
            grupperingsId = sak.grupperingsid,
            nyTilstand = svar.toKalenderTilstand(),
        )
        senderFacade.ferdigstillDineSykmeldteVarsler(varselHendelse)
    }

    private suspend fun handleReferat(
        varselHendelse: NarmesteLederHendelse,
        personData: HentPersonData?,
        narmesteLederId: String,
    ) {
        val sak = getPaagaaendeSak(narmesteLederId)
        if (sak == null) {
            log.warn("handleReferat: Fant ikke sak for narmesteLederId: $narmesteLederId. Sender på gammel måte")
            oldVarselService.sendVarselTilNarmesteLeder(varselHendelse)
            return
        }
        val texts = varselHendelse.dialogmoteNarmesteLederTexts()
        updateSakStatus(sakId = sak.id, grupperingsId = sak.grupperingsid, nyStatus = SakStatus.FERDIG)
        senderFacade.updateKalenderavtale(
            sakId = sak.id,
            grupperingsId = sak.grupperingsid,
            nyTilstand = KalenderTilstand.AVHOLDT,
            nyTekst = personData?.let { "Dialogmøte med ${it.firstName()} er avholdt" } ?: "Dialogmøtet er avholdt",
        )
        val input =
            ArbeidsgiverNotifikasjonInput(
                uuid = UUID.randomUUID(),
                virksomhetsnummer = varselHendelse.orgnummer,
                narmesteLederFnr = varselHendelse.narmesteLederFnr,
                ansattFnr = varselHendelse.arbeidstakerFnr,
                merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
                messageText = texts.messageText,
                epostTittel = texts.epostTittel,
                epostHtmlBody = texts.emailBody,
                meldingstype = Meldingstype.BESKJED,
                grupperingsid = sak.grupperingsid,
            )
        senderFacade.sendTilArbeidsgiverNotifikasjon(varselHendelse, input)
    }

    private suspend fun createNewSak(
        varselHendelse: NarmesteLederHendelse,
        personData: HentPersonData?,
        lenkeTilDialogmoteLanding: String,
        narmestelederId: String,
        motetidspunkt: LocalDateTime,
    ): Pair<String, String> {
        val grupperingsid = UUID.randomUUID().toString()

        val sakInput =
            NySakInput(
                grupperingsid = grupperingsid,
                narmestelederId = narmestelederId,
                merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
                virksomhetsnummer = varselHendelse.orgnummer,
                narmesteLederFnr = varselHendelse.narmesteLederFnr,
                ansattFnr = varselHendelse.arbeidstakerFnr,
                tittel = personData?.let { "Dialogmøte med ${it.fullName()}" } ?: "Innkalling til dialogmøte",
                lenke = lenkeTilDialogmoteLanding,
                initiellStatus = SakStatus.MOTTATT,
                hardDeleteDate = motetidspunkt.plusWeeks(WEEKS_BEFORE_DELETE),
            )
        val sakId = senderFacade.createNewSak(sakInput)

        return Pair(sakId, grupperingsid)
    }

    private suspend fun createNewKalenderavtale(
        sakId: String,
        grupperingsId: String,
        varselHendelse: NarmesteLederHendelse,
        innkallingTekst: String,
        lenkeTilDialogmoteLanding: String,
        ledersEpost: String,
        epostTittel: String,
        epostHtmlBody: String,
        motetidspunkt: LocalDateTime,
    ) {
        val kalenderInput =
            NyKalenderInput(
                sakId = sakId,
                virksomhetsnummer = varselHendelse.orgnummer,
                grupperingsId = grupperingsId,
                merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
                eksternId = UUID.randomUUID().toString(),
                tekst = innkallingTekst,
                ansattFnr = varselHendelse.arbeidstakerFnr,
                narmesteLederFnr = varselHendelse.narmesteLederFnr,
                startTidspunkt = motetidspunkt,
                lenke = lenkeTilDialogmoteLanding,
                kalenderavtaleTilstand = KalenderTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
                ledersEpost = ledersEpost,
                epostTittel = epostTittel,
                epostHtmlBody = epostHtmlBody,
            )
        senderFacade.createNewKalenderavtale(kalenderInput)
    }

    private suspend fun updateSakStatus(
        sakId: String,
        grupperingsId: String,
        nyStatus: SakStatus,
        nyttMotetidspunkt: LocalDateTime? = null,
    ) {
        senderFacade.nyStatusSak(
            sakId = sakId,
            NyStatusSakInput(
                grupperingsId = grupperingsId,
                merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
                sakStatus = nyStatus,
                oppdatertTidspunkt = nyttMotetidspunkt,
                oppdatertHardDeleteDateTime = nyttMotetidspunkt?.plusWeeks(WEEKS_BEFORE_DELETE),
            ),
        )
    }

    private fun getPaagaaendeSak(narmesteLederId: String): PSakInput? =
        senderFacade.getPaagaaendeSak(
            narmesteLederId = narmesteLederId,
            merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
        )

    private fun sendVarselTilDineSykmeldte(varselHendelse: NarmesteLederHendelse) {
        val varselText = varselHendelse.dialogmoteNarmesteLederTexts().dineSykmeldteText
        val dineSykmeldteVarsel =
            DineSykmeldteVarsel(
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
