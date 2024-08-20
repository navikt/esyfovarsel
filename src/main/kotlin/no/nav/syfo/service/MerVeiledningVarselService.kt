package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT
import no.nav.syfo.DITT_SYKEFRAVAER_MER_VEILEDNING_MESSAGE_TEXT
import no.nav.syfo.Environment
import no.nav.syfo.MER_VEILEDNING_URL
import no.nav.syfo.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.behandlendeenhet.domain.isPilot
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.consumer.pdfgen.PdfgenClient
import no.nav.syfo.isProdGcp
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.OPPGAVE
import no.nav.syfo.utils.dataToVarselData
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

const val DITT_SYKEFRAVAER_HENDELSE_TYPE_MER_VEILEDNING = "ESYFOVARSEL_MER_VEILEDNING"

class MerVeiledningVarselService(
    val senderFacade: SenderFacade,
    val env: Environment,
    val pdfgenConsumer: PdfgenClient,
    val dokarkivService: DokarkivService,
    val accessControlService: AccessControlService,
    val behandlendeEnhetClient: BehandlendeEnhetClient,
) {
    private val log = LoggerFactory.getLogger(MerVeiledningVarselService::class.qualifiedName)

    suspend fun sendVarselTilArbeidstakerFromJob(
        arbeidstakerHendelse: ArbeidstakerHendelse,
        planlagtVarselUuid: String,
    ) {
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerHendelse.arbeidstakerFnr)
        val isBrukerReservert = !userAccessStatus.canUserBeDigitallyNotified
        val isPilotbruker =
            behandlendeEnhetClient.getBehandlendeEnhet(arbeidstakerHendelse.arbeidstakerFnr)
                ?.isPilot(env.isProdGcp()) == true

        when {
            isBrukerReservert -> {
                sendInformasjonTilReserverte(arbeidstakerHendelse, planlagtVarselUuid)
            }

            isPilotbruker -> {
                sendInformasjonTilDigitalePilotBrukere(arbeidstakerHendelse, planlagtVarselUuid)
            }

            else -> {
                sendInformasjonTilDigitaleIkkePilotBrukere(arbeidstakerHendelse, planlagtVarselUuid)
            }
        }

        sendOppgaveTilDittSykefravaer(arbeidstakerHendelse.arbeidstakerFnr, planlagtVarselUuid, arbeidstakerHendelse)
    }

    private suspend fun sendInformasjonTilReserverte(
        arbeidstakerHendelse: ArbeidstakerHendelse,
        planlagtVarselUuid: String
    ) {
        val pdf = pdfgenConsumer.getMerVeiledningPdfForReserverte(arbeidstakerHendelse.arbeidstakerFnr)

        val journalpostId = pdf?.let {
            dokarkivService.journalforDokument(
                fnr = arbeidstakerHendelse.arbeidstakerFnr,
                uuid = planlagtVarselUuid,
                pdf = it,
            )
        }

        log.info("Forsøkte å journalføre SSPS til reservert bruker i dokarkiv, journalpostId er $journalpostId")

        sendBrevVarselTilArbeidstaker(planlagtVarselUuid, arbeidstakerHendelse, journalpostId!!)
    }

    private suspend fun sendInformasjonTilDigitalePilotBrukere(
        arbeidstakerHendelse: ArbeidstakerHendelse,
        planlagtVarselUuid: String
    ) {
        val pdf =
            pdfgenConsumer.getMerVeiledningPdfForDigitalePilotBrukere(arbeidstakerHendelse.arbeidstakerFnr)

        val journalpostId = pdf?.let {
            dokarkivService.journalforDokument(
                arbeidstakerHendelse.arbeidstakerFnr,
                planlagtVarselUuid,
                it,
            )
        }

        if (journalpostId != null) {
            log.info("Journalførte SSPS for pilotbruker i dokarkiv, journalpostId er $journalpostId")
        } else {
            log.error("Kunne ikke journalføre SSPS for pilotbruker i dokarkiv for planlagt uuid $planlagtVarselUuid.")
        }

        sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse)
    }

    private suspend fun sendInformasjonTilDigitaleIkkePilotBrukere(
        arbeidstakerHendelse: ArbeidstakerHendelse,
        planlagtVarselUuid: String
    ) {
        val pdf =
            pdfgenConsumer.getMerVeiledningPdfForDigitale(arbeidstakerHendelse.arbeidstakerFnr)

        val journalpostId = pdf?.let {
            dokarkivService.journalforDokument(
                arbeidstakerHendelse.arbeidstakerFnr,
                planlagtVarselUuid,
                it,
            )
        }

        log.info("Forsøkte å journalføre SSPS til bruker som ikke er reservert i dokarkiv, journalpostId er $journalpostId")

        sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse)
    }

    suspend fun sendVarselTilArbeidstaker(
        arbeidstakerHendelse: ArbeidstakerHendelse,
    ) {
        val data = dataToVarselData(arbeidstakerHendelse.data)
        requireNotNull(data.journalpost)
        requireNotNull(data.journalpost.id)
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerHendelse.arbeidstakerFnr)

        if (userAccessStatus.canUserBeDigitallyNotified) {
            sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse)
        } else {
            senderFacade.sendBrevTilFysiskPrint(
                data.journalpost.uuid,
                arbeidstakerHendelse,
                data.journalpost.id,
                DistibusjonsType.VIKTIG,
            )
        }
        sendOppgaveTilDittSykefravaer(
            arbeidstakerHendelse.arbeidstakerFnr,
            UUID.randomUUID().toString(),
            arbeidstakerHendelse
        )
    }

    private fun sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse: ArbeidstakerHendelse) {
        val uuid = "${UUID.randomUUID()}"
        val fnr = arbeidstakerHendelse.arbeidstakerFnr
        val url = URL(env.urlEnv.baseUrlNavEkstern + MER_VEILEDNING_URL)

        senderFacade.sendTilBrukernotifikasjoner(
            uuid = uuid,
            mottakerFnr = fnr,
            content = BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT,
            url = url,
            varselHendelse = arbeidstakerHendelse,
            varseltype = OPPGAVE
        )
    }

    private suspend fun sendBrevVarselTilArbeidstaker(
        uuid: String,
        arbeidstakerHendelse: ArbeidstakerHendelse,
        journalpostId: String,
    ) {
        try {
            senderFacade.sendBrevTilFysiskPrint(uuid, arbeidstakerHendelse, journalpostId)
        } catch (e: RuntimeException) {
            log.info("Feil i sending av fysisk brev om mer veildning: ${e.message}")
        }
    }

    private fun sendOppgaveTilDittSykefravaer(
        fnr: String,
        uuid: String,
        arbeidstakerHendelse: ArbeidstakerHendelse,
    ) {
        val melding = DittSykefravaerMelding(
            OpprettMelding(
                DITT_SYKEFRAVAER_MER_VEILEDNING_MESSAGE_TEXT,
                MER_VEILEDNING_URL,
                Variant.INFO,
                true,
                DITT_SYKEFRAVAER_HENDELSE_TYPE_MER_VEILEDNING,
                LocalDateTime.now().plusWeeks(13).toInstant(ZoneOffset.UTC),
            ),
            null,
            fnr,
        )
        senderFacade.sendTilDittSykefravaer(
            arbeidstakerHendelse,
            DittSykefravaerVarsel(
                uuid,
                melding,
            ),
        )
    }
}
