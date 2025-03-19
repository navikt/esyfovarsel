package no.nav.syfo.service

import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.Kanal.ARBEIDSGIVERNOTIFIKASJON
import no.nav.syfo.db.domain.Kanal.BREV
import no.nav.syfo.db.domain.Kanal.BRUKERNOTIFIKASJON
import no.nav.syfo.db.domain.Kanal.DINE_SYKMELDTE
import no.nav.syfo.db.domain.Kanal.DITT_SYKEFRAVAER
import no.nav.syfo.db.domain.PKalenderInput
import no.nav.syfo.db.domain.PSakInput
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.db.fetchUferdigstilteNarmesteLederVarsler
import no.nav.syfo.db.fetchUferdigstilteVarsler
import no.nav.syfo.db.fetchUtsendtVarselByJournalpostId
import no.nav.syfo.db.getArbeidsgivernotifikasjonerKalenderavtale
import no.nav.syfo.db.getPaagaaendeArbeidsgivernotifikasjonerSak
import no.nav.syfo.db.setUferdigstiltUtsendtVarselToForcedLetter
import no.nav.syfo.db.setUtsendtVarselToFerdigstilt
import no.nav.syfo.db.storeArbeidsgivernotifikasjonerKalenderavtale
import no.nav.syfo.db.storeArbeidsgivernotifikasjonerSak
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.db.storeUtsendtVarselFeilet
import no.nav.syfo.db.updateArbeidsgivernotifikasjonerSakStatus
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.KalenderTilstand
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyKalenderInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyStatusSakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.OppdaterKalenderInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.toPKalenderInput
import no.nav.syfo.utils.enumValueOfOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.LocalDateTime
import java.util.*

class SenderFacade(
    private val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    private val dittSykefravaerMeldingKafkaProducer: DittSykefravaerMeldingKafkaProducer,
    private val brukernotifikasjonerService: BrukernotifikasjonerService,
    private val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    private val fysiskBrevUtsendingService: FysiskBrevUtsendingService,
    val database: DatabaseInterface,
) {
    private val log: Logger = LoggerFactory.getLogger(SenderFacade::class.qualifiedName)
    fun sendTilDineSykmeldte(
        varselHendelse: NarmesteLederHendelse,
        varsel: DineSykmeldteVarsel,
    ) {
        try {
            dineSykmeldteHendelseKafkaProducer.sendVarsel(varsel)
            lagreUtsendtNarmesteLederVarsel(DINE_SYKMELDTE, varselHendelse, varsel.id.toString())
        } catch (e: Exception) {
            log.error("Error while sending varsel to DINE_SYKMELDTE: ${e.message}", e)
            lagreIkkeUtsendtNarmesteLederVarsel(
                kanal = DINE_SYKMELDTE,
                varselHendelse = varselHendelse,
                eksternReferanse = varsel.id.toString(),
                feilmelding = e.message,
                merkelapp = null,
            )
        }
    }

    fun sendTilDittSykefravaer(
        varselHendelse: ArbeidstakerHendelse,
        varsel: DittSykefravaerVarsel,
    ) {
        try {
            val eksternUUID = dittSykefravaerMeldingKafkaProducer.sendMelding(varsel.melding, varsel.uuid)
            lagreUtsendtArbeidstakerVarsel(
                kanal = DITT_SYKEFRAVAER,
                arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
                orgnummer = varselHendelse.orgnummer,
                hendelseType = varselHendelse.type.name,
                eksternReferanse = eksternUUID
            )
        } catch (e: Exception) {
            log.error("Error while sending varsel to DITT_SYKEFRAVAER: ${e.message}")
            lagreIkkeUtsendtArbeidstakerVarsel(
                kanal = DITT_SYKEFRAVAER,
                arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
                orgnummer = varselHendelse.orgnummer,
                hendelseType = varselHendelse.type.name,
                eksternReferanse = varsel.uuid,
                feilmelding = e.message,
                journalpostId = null,
                brukernotifikasjonerMeldingType = null,
                isForcedLetter = false,
            )
        }
    }

    fun sendTilBrukernotifikasjoner(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL? = null,
        arbeidstakerFnr: String,
        orgnummer: String?,
        hendelseType: String,
        varseltype: InternalBrukernotifikasjonType,
        eksternVarsling: Boolean = true,
        smsContent: String? = null,
        dagerTilDeaktivering: Long? = null,
        journalpostId: String? = null,
        storeFailedUtsending: Boolean = true,
    ): Boolean {
        try {
            brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                uuid = uuid,
                mottakerFnr = mottakerFnr,
                content = content,
                url = url,
                varseltype = varseltype,
                eksternVarsling = eksternVarsling,
                smsContent = smsContent,
                dagerTilDeaktivering = dagerTilDeaktivering,
            )
            lagreUtsendtArbeidstakerVarsel(
                kanal = BRUKERNOTIFIKASJON,
                arbeidstakerFnr = arbeidstakerFnr,
                orgnummer = orgnummer,
                hendelseType = hendelseType,
                eksternReferanse = uuid,
                journalpostId = journalpostId,
            )
            return true
        } catch (e: Exception) {
            log.error("Error while sending varsel to BRUKERNOTIFIKASJON: ${e.message}")
            if (storeFailedUtsending) {
                lagreIkkeUtsendtArbeidstakerVarsel(
                    kanal = BRUKERNOTIFIKASJON,
                    arbeidstakerFnr = arbeidstakerFnr,
                    orgnummer = orgnummer,
                    hendelseType = hendelseType,
                    eksternReferanse = uuid,
                    feilmelding = e.message,
                    journalpostId = journalpostId,
                    brukernotifikasjonerMeldingType = varseltype.name,
                    isForcedLetter = false,
                )
            }
            return false
        }
    }

    suspend fun sendTilArbeidsgiverNotifikasjon(
        varselHendelse: NarmesteLederHendelse,
        varsel: ArbeidsgiverNotifikasjonInput,
    ) {
        var isSendingSucceed = true
        try {
            arbeidsgiverNotifikasjonService.sendNotifikasjon(varsel)
        } catch (e: Exception) {
            log.error("Error while sending varsel to ARBEIDSGIVERNOTIFIKASJON: ${e.message}")
            isSendingSucceed = false
            lagreIkkeUtsendtNarmesteLederVarsel(
                kanal = ARBEIDSGIVERNOTIFIKASJON,
                varselHendelse = varselHendelse,
                eksternReferanse = varsel.uuid.toString(),
                feilmelding = e.message,
                merkelapp = varsel.merkelapp,
            )
        }
        if (isSendingSucceed) {
            lagreUtsendtNarmesteLederVarsel(
                ARBEIDSGIVERNOTIFIKASJON,
                varselHendelse,
                varsel.uuid.toString(),
                varsel.merkelapp,
            )
        }
    }

    suspend fun createNewKalenderavtale(
        kalenderInput: NyKalenderInput
    ) {
        val kalenderId = arbeidsgiverNotifikasjonService.createNewKalenderavtale(kalenderInput)
        require(kalenderId != null) { "Failed to create new kalenderavtale" }

        database.storeArbeidsgivernotifikasjonerKalenderavtale(kalenderInput.toPKalenderInput(kalenderId))
    }

    suspend fun createNewSak(sakInput: NySakInput): String {
        val createdSak = arbeidsgiverNotifikasjonService.createNewSak(sakInput)
        require(createdSak != null) { "Failed to create new sak" }

        return database.storeArbeidsgivernotifikasjonerSak(sakInput)
    }

    fun getPaagaaendeSak(narmesteLederId: String, merkelapp: String): PSakInput? {
        return database.getPaagaaendeArbeidsgivernotifikasjonerSak(
            narmestelederId = narmesteLederId,
            merkelapp = merkelapp
        )
    }

    suspend fun nyStatusSak(
        sakId: String,
        nyStatusSakInput: NyStatusSakInput
    ) {
        val oppdatertSak = arbeidsgiverNotifikasjonService.nyStatusSak(nyStatusSakInput)
        require(oppdatertSak != null) { "Failed to update sak" }
        database.updateArbeidsgivernotifikasjonerSakStatus(sakId, nyStatusSakInput.sakStatus)
    }

    suspend fun updateKalenderavtale(
        sakId: String,
        grupperingsId: String,
        nyTilstand: KalenderTilstand,
        nyTekst: String? = null,
        hardDeleteTidspunkt: LocalDateTime? = null,
        ledersEpost: String? = null,
        epostTittel: String? = null,
        epostHtmlBody: String? = null,
    ) {
        val storedKalenderAvtale = database.getArbeidsgivernotifikasjonerKalenderavtale(sakId)
        require(storedKalenderAvtale != null) { "Kalenderavtale not found for sakId: $sakId" }

        val oppdaterKalenderInput = OppdaterKalenderInput(
            id = storedKalenderAvtale.kalenderId,
            nyTilstand = nyTilstand,
            nyTekst = nyTekst,
            hardDeleteTidspunkt = hardDeleteTidspunkt,
            ledersEpost = ledersEpost,
            epostTittel = epostTittel,
            epostHtmlBody = epostHtmlBody
        )

        val kalenderId = arbeidsgiverNotifikasjonService.updateKalenderavtale(oppdaterKalenderInput)
        require(kalenderId != null) { "Failed to update kalenderavtale" }

        database.storeArbeidsgivernotifikasjonerKalenderavtale(
            PKalenderInput(
                sakId = sakId,
                eksternId = storedKalenderAvtale.eksternId,
                grupperingsid = grupperingsId,
                merkelapp = storedKalenderAvtale.merkelapp,
                kalenderId = kalenderId,
                tekst = oppdaterKalenderInput.nyTekst ?: storedKalenderAvtale.tekst,
                startTidspunkt = storedKalenderAvtale.startTidspunkt,
                sluttTidspunkt = storedKalenderAvtale.sluttTidspunkt,
                kalenderavtaleTilstand = oppdaterKalenderInput.nyTilstand
                    ?: storedKalenderAvtale.kalenderavtaleTilstand,
                hardDeleteDate = oppdaterKalenderInput.hardDeleteTidspunkt
            )
        )
    }

    suspend fun ferdigstillVarslerForFnr(fnr: PersonIdent) {
        fetchUferdigstilteVarsler(fnr)
            .forEach { ferdigstillVarsel(it) }
    }

    suspend fun ferdigstillArbeidstakerVarsler(varselHendelse: ArbeidstakerHendelse) {
        fetchUferdigstilteVarsler(
            PersonIdent(varselHendelse.arbeidstakerFnr),
            varselHendelse.orgnummer,
            setOf(varselHendelse.type),
        )
            .forEach { ferdigstillVarsel(it) }
    }

    suspend fun ferdigstillNarmesteLederVarsler(varselHendelse: NarmesteLederHendelse) {
        fetchUferdigstilteVarsler(
            PersonIdent(varselHendelse.arbeidstakerFnr),
            varselHendelse.orgnummer,
            setOf(varselHendelse.type),
        )
            .forEach { ferdigstillVarsel(it) }
    }

    suspend fun ferdigstillDittSykefravaerVarsler(varselHendelse: ArbeidstakerHendelse) {
        fetchUferdigstilteVarsler(
            PersonIdent(varselHendelse.arbeidstakerFnr),
            varselHendelse.orgnummer,
            setOf(varselHendelse.type),
            DITT_SYKEFRAVAER,
        )
            .forEach { ferdigstillVarsel(it) }
    }

    suspend fun ferdigstillDineSykmeldteVarsler(varselHendelse: NarmesteLederHendelse) {
        fetchUferdigstilteNarmesteLederVarsler(
            arbeidstakerFnr = PersonIdent(varselHendelse.arbeidstakerFnr),
            narmesteLederFnr = PersonIdent(varselHendelse.narmesteLederFnr),
            orgnummer = varselHendelse.orgnummer,
            kanal = DINE_SYKMELDTE,
        )
            .forEach { ferdigstillVarsel(it) }
    }

    suspend fun ferdigstillDittSykefravaerVarslerAvTyper(
        varselHendelse: ArbeidstakerHendelse,
        varselTyper: Set<HendelseType>,
    ) {
        fetchUferdigstilteVarsler(
            PersonIdent(varselHendelse.arbeidstakerFnr),
            varselHendelse.orgnummer,
            varselTyper,
            DITT_SYKEFRAVAER,
        )
            .forEach { ferdigstillVarsel(it) }
    }

    fun fetchUferdigstilteVarsler(
        arbeidstakerFnr: PersonIdent,
        orgnummer: String? = null,
        types: Set<HendelseType> = emptySet(),
        kanal: Kanal? = null,
    ): List<PUtsendtVarsel> {
        return database.fetchUferdigstilteVarsler(arbeidstakerFnr)
            .filter { orgnummer == null || it.orgnummer == orgnummer }
            .filter { types.isEmpty() || types.contains(enumValueOfOrNull<HendelseType>(it.type)) }
            .filter { kanal == null || it.kanal == kanal.name }
    }

    fun fetchUferdigstilteNarmesteLederVarsler(
        arbeidstakerFnr: PersonIdent,
        narmesteLederFnr: PersonIdent,
        orgnummer: String? = null,
        types: Set<HendelseType> = emptySet(),
        kanal: Kanal? = null,
    ): List<PUtsendtVarsel> {
        return database.fetchUferdigstilteNarmesteLederVarsler(
            sykmeldtFnr = arbeidstakerFnr,
            narmesteLederFnr = narmesteLederFnr
        )
            .filter { orgnummer == null || it.orgnummer == orgnummer }
            .filter { types.isEmpty() || types.contains(enumValueOfOrNull<HendelseType>(it.type)) }
            .filter { kanal == null || it.kanal == kanal.name }
    }

    private suspend fun ferdigstillVarsel(utsendtVarsel: PUtsendtVarsel) {
        if (utsendtVarsel.eksternReferanse != null && utsendtVarsel.ferdigstiltTidspunkt == null) {
            when (utsendtVarsel.kanal) {
                BRUKERNOTIFIKASJON.name -> {
                    brukernotifikasjonerService.ferdigstillVarsel(utsendtVarsel.eksternReferanse)
                }

                DITT_SYKEFRAVAER.name -> {
                    dittSykefravaerMeldingKafkaProducer.ferdigstillMelding(
                        utsendtVarsel.eksternReferanse,
                        utsendtVarsel.fnr,
                    )
                }

                DINE_SYKMELDTE.name -> {
                    dineSykmeldteHendelseKafkaProducer.ferdigstillVarsel(utsendtVarsel.eksternReferanse)
                }

                ARBEIDSGIVERNOTIFIKASJON.name -> {
                    if (utsendtVarsel.arbeidsgivernotifikasjonMerkelapp != null) {
                        arbeidsgiverNotifikasjonService.deleteNotifikasjon(
                            utsendtVarsel.arbeidsgivernotifikasjonMerkelapp,
                            utsendtVarsel.eksternReferanse,
                        )
                    }
                }
            }
            database.setUtsendtVarselToFerdigstilt(utsendtVarsel.eksternReferanse)
        }
    }

    suspend fun sendBrevTilFysiskPrint(
        uuid: String,
        varselHendelse: ArbeidstakerHendelse,
        journalpostId: String,
        distribusjonsType: DistibusjonsType = DistibusjonsType.ANNET,
        storeFailedUtsending: Boolean = true,
    ): Boolean {
        var isSendingSucceed = true
        var isConflictedResponse = false
        try {
            fysiskBrevUtsendingService.sendBrev(uuid, journalpostId, distribusjonsType)
        } catch (e: IllegalStateException) {
            isConflictedResponse = true
            log.error("Already sent journalPostId: ${e.message}")
        } catch (e: Exception) {
            isSendingSucceed = false
            log.error("Error while sending brev til fysisk print: ${e.message}")
            if (storeFailedUtsending) {
                lagreIkkeUtsendtArbeidstakerVarsel(
                    kanal = BREV,
                    arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
                    orgnummer = varselHendelse.orgnummer,
                    hendelseType = varselHendelse.type.name,
                    eksternReferanse = uuid,
                    feilmelding = e.message,
                    journalpostId = journalpostId,
                    brukernotifikasjonerMeldingType = null,
                    isForcedLetter = false,
                )
            }
        }
        if (isSendingSucceed || isConflictedResponse) {
            if (database.fetchUtsendtVarselByJournalpostId(journalpostId).isEmpty()) {
                lagreUtsendtArbeidstakerVarsel(
                    BREV,
                    arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
                    orgnummer = varselHendelse.orgnummer,
                    hendelseType = varselHendelse.type.name,
                    eksternReferanse = uuid,
                    journalpostId = journalpostId
                )
            }
            return true
        }
        return false
    }

    suspend fun sendBrevTilTvingSentralPrint(
        uuid: String,
        varselHendelse: ArbeidstakerHendelse,
        journalpostId: String,
        distribusjonsType: DistibusjonsType = DistibusjonsType.VIKTIG,
    ) {
        try {
            fysiskBrevUtsendingService.sendBrev(uuid, journalpostId, distribusjonsType, tvingSentralPrint = true)
            log.info(
                "[RENOTIFICATE VIA SENTRAL PRINT DIRECTLY]: sending direct sentral print letter with journalpostId $journalpostId succeded, storing in database"
            )
            lagreUtsendtArbeidstakerVarsel(
                kanal = BREV,
                arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
                orgnummer = varselHendelse.orgnummer,
                hendelseType = varselHendelse.type.name,
                eksternReferanse = uuid,
                isForcedLetter = true,
                journalpostId = journalpostId
            )
            database.setUferdigstiltUtsendtVarselToForcedLetter(eksternRef = uuid)
        } catch (e: Exception) {
            log.error(
                "[RENOTIFICATE VIA SENTRAL PRINT DIRECTLY]: Error while sending brev til direct sentral print: ${e.message}"
            )
        }
    }

    private fun lagreUtsendtNarmesteLederVarsel(
        kanal: Kanal,
        varselHendelse: NarmesteLederHendelse,
        eksternReferanse: String,
        arbeidsgivernotifikasjonMerkelapp: String? = null,
    ) {
        database.storeUtsendtVarsel(
            PUtsendtVarsel(
                UUID.randomUUID().toString(),
                varselHendelse.arbeidstakerFnr,
                null,
                varselHendelse.narmesteLederFnr,
                varselHendelse.orgnummer,
                varselHendelse.type.name,
                kanal.name,
                LocalDateTime.now(),
                null,
                eksternReferanse,
                null,
                arbeidsgivernotifikasjonMerkelapp,
                isForcedLetter = false,
                null,
            ),
        )
    }

    fun lagreUtsendtArbeidstakerVarsel(
        kanal: Kanal,
        arbeidstakerFnr: String,
        orgnummer: String?,
        hendelseType: String,
        eksternReferanse: String,
        isForcedLetter: Boolean = false,
        journalpostId: String? = null,
    ) {
        database.storeUtsendtVarsel(
            PUtsendtVarsel(
                UUID.randomUUID().toString(),
                arbeidstakerFnr,
                null,
                null,
                orgnummer,
                hendelseType,
                kanal.name,
                LocalDateTime.now(),
                null,
                eksternReferanse,
                null,
                null,
                isForcedLetter,
                journalpostId,
            ),
        )
    }

    private fun lagreIkkeUtsendtArbeidstakerVarsel(
        kanal: Kanal,
        arbeidstakerFnr: String,
        orgnummer: String?,
        hendelseType: String,
        eksternReferanse: String,
        feilmelding: String?,
        journalpostId: String? = null,
        brukernotifikasjonerMeldingType: String? = null,
        isForcedLetter: Boolean,
    ) {
        database.storeUtsendtVarselFeilet(
            PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = eksternReferanse,
                arbeidstakerFnr = arbeidstakerFnr,
                narmesteLederFnr = null,
                orgnummer = orgnummer,
                hendelsetypeNavn = hendelseType,
                arbeidsgivernotifikasjonMerkelapp = null,
                brukernotifikasjonerMeldingType = brukernotifikasjonerMeldingType,
                journalpostId = journalpostId,
                kanal = kanal.name,
                feilmelding = feilmelding,
                utsendtForsokTidspunkt = LocalDateTime.now(),
                isForcedLetter = isForcedLetter,
            ),
        )
    }

    private fun lagreIkkeUtsendtNarmesteLederVarsel(
        kanal: Kanal,
        varselHendelse: NarmesteLederHendelse,
        eksternReferanse: String,
        feilmelding: String?,
        merkelapp: String?,
    ) {
        database.storeUtsendtVarselFeilet(
            PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = eksternReferanse,
                arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
                narmesteLederFnr = varselHendelse.narmesteLederFnr,
                orgnummer = varselHendelse.orgnummer,
                hendelsetypeNavn = varselHendelse.type.name,
                arbeidsgivernotifikasjonMerkelapp = merkelapp,
                brukernotifikasjonerMeldingType = null,
                journalpostId = null,
                kanal = kanal.name,
                feilmelding = feilmelding,
                utsendtForsokTidspunkt = LocalDateTime.now(),
                isForcedLetter = false,
            ),
        )
    }

    enum class InternalBrukernotifikasjonType {
        OPPGAVE,
        BESKJED,
        DONE
    }
}
