package no.nav.syfo.service

import java.net.URL
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.Kanal.*
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.db.fetchUferdigstilteVarsler
import no.nav.syfo.db.setUtsendtVarselToFerdigstilt
import no.nav.syfo.db.storeUtsendtMerVeiledningVarselBackup
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.db.storeUtsendtVarselFeilet
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.utils.enumValueOfOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
            log.warn("Error while sending varsel to DINE_SYKMELDTE: ${e.message}", e)
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
            lagreUtsendtArbeidstakerVarsel(DITT_SYKEFRAVAER, varselHendelse, eksternUUID)
        } catch (e: Exception) {
            log.warn("Error while sending varsel to DITT_SYKEFRAVAER: ${e.message}")
            lagreIkkeUtsendtArbeidstakerVarsel(
                kanal = DITT_SYKEFRAVAER,
                varselHendelse = varselHendelse,
                eksternReferanse = varsel.uuid,
                feilmelding = e.message,
                journalpostId = null,
                brukernotifikasjonerMeldingType = null,
            )
        }
    }

    fun sendTilBrukernotifikasjoner(
        uuid: String,
        mottakerFnr: String,
        content: String,
        url: URL? = null,
        varselHendelse: ArbeidstakerHendelse,
        varseltype: InternalBrukernotifikasjonType,
        eksternVarsling: Boolean = true,
        smsContent: String? = null,
        dagerTilDeaktivering: Long? = null,
    ) {
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
                varselHendelse = varselHendelse,
                eksternReferanse = uuid
            )
        } catch (e: Exception) {
            log.warn("Error while sending varsel to BRUKERNOTIFIKASJON: ${e.message}")
            lagreIkkeUtsendtArbeidstakerVarsel(
                kanal = BRUKERNOTIFIKASJON,
                varselHendelse = varselHendelse,
                eksternReferanse = uuid,
                feilmelding = e.message,
                journalpostId = null,
                brukernotifikasjonerMeldingType = varseltype.name,
            )
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
            log.warn("Error while sending varsel to ARBEIDSGIVERNOTIFIKASJON: ${e.message}")
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
    ) {
        var isSendingSucceed = true
        try {
            fysiskBrevUtsendingService.sendBrev(uuid, journalpostId, distribusjonsType)
        } catch (e: Exception) {
            isSendingSucceed = false
            log.warn("Error while sending brev til fysisk print: ${e.message}")
            lagreIkkeUtsendtArbeidstakerVarsel(
                kanal = BREV,
                varselHendelse = varselHendelse,
                eksternReferanse = uuid,
                feilmelding = e.message,
                journalpostId = journalpostId,
                brukernotifikasjonerMeldingType = null,
            )
        }
        if (isSendingSucceed) {
            lagreUtsendtArbeidstakerVarsel(BREV, varselHendelse, uuid)
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
            ),
        )
    }

    fun lagreUtsendtArbeidstakerVarsel(
        kanal: Kanal,
        varselHendelse: ArbeidstakerHendelse,
        eksternReferanse: String,
    ) {
        database.storeUtsendtVarsel(
            PUtsendtVarsel(
                UUID.randomUUID().toString(),
                varselHendelse.arbeidstakerFnr,
                null,
                null,
                varselHendelse.orgnummer,
                varselHendelse.type.name,
                kanal.name,
                LocalDateTime.now(),
                null,
                eksternReferanse,
                null,
                null,
            ),
        )
    }

    fun lagreUtsendtMerVeiledningVarselBackUp(
        kanal: Kanal,
        varselHendelse: ArbeidstakerHendelse,
        eksternReferanse: String,
    ) {
        if (varselHendelse.type == HendelseType.SM_MER_VEILEDNING) {
            log.info("Storing backup mer veiledning varsel")
            database.storeUtsendtMerVeiledningVarselBackup(
                PUtsendtVarsel(
                    UUID.randomUUID().toString(),
                    varselHendelse.arbeidstakerFnr,
                    null,
                    null,
                    null,
                    varselHendelse.type.name,
                    kanal.name,
                    LocalDateTime.now(),
                    null,
                    eksternReferanse,
                    null,
                    null,
                ),
            )
        }
    }

    private fun lagreIkkeUtsendtArbeidstakerVarsel(
        kanal: Kanal,
        varselHendelse: ArbeidstakerHendelse,
        eksternReferanse: String,
        feilmelding: String?,
        journalpostId: String? = null,
        brukernotifikasjonerMeldingType: String? = null,
    ) {
        database.storeUtsendtVarselFeilet(
            PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = eksternReferanse,
                arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
                narmesteLederFnr = null,
                orgnummer = varselHendelse.orgnummer,
                hendelsetypeNavn = varselHendelse.type.name,
                arbeidsgivernotifikasjonMerkelapp = null,
                brukernotifikasjonerMeldingType = brukernotifikasjonerMeldingType,
                journalpostId = journalpostId,
                kanal = kanal.name,
                feilmelding = feilmelding,
                utsendtForsokTidspunkt = LocalDateTime.now(),
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
            ),
        )
    }

    enum class InternalBrukernotifikasjonType {
        OPPGAVE,
        BESKJED,
        DONE
    }
}
