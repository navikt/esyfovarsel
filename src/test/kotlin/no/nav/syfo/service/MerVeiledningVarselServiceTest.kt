package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.behandlendeenhet.domain.BehandlendeEnhet
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.consumer.pdfgen.PdfgenClient
import no.nav.syfo.getTestEnv
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType

class MerVeiledningVarselServiceTest : DescribeSpec({
    val senderFacade = mockk<SenderFacade>(relaxed = true)
    val pdfgenClient = mockk<PdfgenClient>(relaxed = true)
    val dokarkivService = mockk<DokarkivService>(relaxed = true)
    val accessControlService = mockk<AccessControlService>(relaxed = true)
    val behandlendeEnhetClient = mockk<BehandlendeEnhetClient>(relaxed = true)
    val testEnv = getTestEnv()
    val merveiledningVarselService = MerVeiledningVarselService(
        senderFacade = senderFacade,
        env = testEnv,
        pdfgenConsumer = pdfgenClient,
        dokarkivService = dokarkivService,
        accessControlService = accessControlService,
        behandlendeEnhetClient = behandlendeEnhetClient,
    )

    beforeTest {
        clearAllMocks()
    }

    describe("Varsel om mer veiledning") {
        it("Sender varsel til dokdistribusjon dersom reservert") {
            coEvery { accessControlService.getUserAccessStatus(any()) } returns UserAccessStatus(
                fnr = SM_FNR,
                canUserBeDigitallyNotified = false
            )

            val journalpostId = "420049753"
            val journalpostUuid = "cda0b55a-df72-4888-a5a5-6bfa74cacafe"

            val hendelse = ArbeidstakerHendelse(
                type = HendelseType.SM_MER_VEILEDNING,
                ferdigstill = false,
                data = varselData(journalpostId = journalpostId, journalpostUuid = journalpostUuid),
                arbeidstakerFnr = SM_FNR,
                orgnummer = null,
            )

            merveiledningVarselService.sendVarselTilArbeidstaker(hendelse)

            coVerify(exactly = 0) {
                senderFacade.sendTilBrukernotifikasjoner(
                    uuid = any(),
                    mottakerFnr = SM_FNR,
                    content = any(),
                    url = any(),
                    varselHendelse = hendelse,
                    varseltype = SenderFacade.InternalBrukernotifikasjonType.OPPGAVE
                )
            }

            coVerify(exactly = 1) {
                senderFacade.sendBrevTilFysiskPrint(
                    journalpostUuid,
                    hendelse,
                    journalpostId,
                    DistibusjonsType.VIKTIG,
                )
            }
        }

        it("Sender varsel til brukernotifikasjoner dersom ikke reservert") {
            coEvery { accessControlService.getUserAccessStatus(any()) } returns UserAccessStatus(
                fnr = SM_FNR,
                canUserBeDigitallyNotified = true
            )

            val journalpostId = "420049753"
            val journalpostUuid = "cda0b55a-df72-4888-a5a5-6bfa74cacafe"

            val hendelse = ArbeidstakerHendelse(
                type = HendelseType.SM_MER_VEILEDNING,
                ferdigstill = false,
                data = varselData(journalpostId = journalpostId, journalpostUuid = journalpostUuid),
                arbeidstakerFnr = SM_FNR,
                orgnummer = null,
            )

            merveiledningVarselService.sendVarselTilArbeidstaker(hendelse)

            coVerify(exactly = 0) {
                senderFacade.sendBrevTilFysiskPrint(
                    journalpostUuid,
                    hendelse,
                    journalpostId,
                    DistibusjonsType.VIKTIG,
                )
            }

            coVerify(exactly = 1) {
                senderFacade.sendTilBrukernotifikasjoner(
                    uuid = any(),
                    mottakerFnr = SM_FNR,
                    content = BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT,
                    url = any(),
                    varselHendelse = hendelse,
                    varseltype = SenderFacade.InternalBrukernotifikasjonType.OPPGAVE
                )
            }
        }

        it("Pilotbrukere får journalført egen variant av PDF, og får også digitalt varsel") {
            coEvery { accessControlService.getUserAccessStatus(any()) } returns UserAccessStatus(
                fnr = SM_FNR,
                canUserBeDigitallyNotified = true
            )

            val journalpostId = "420049753"
            val journalpostUuid = "cda0b55a-df72-4888-a5a5-6bfa74cacafe"
            val planlagtVarselUuid = "bda0b55a-hf72-4888-a5a5-6bfa74cacafv"

            val hendelse = ArbeidstakerHendelse(
                type = HendelseType.SM_MER_VEILEDNING,
                ferdigstill = false,
                data = varselData(journalpostId = journalpostId, journalpostUuid = journalpostUuid),
                arbeidstakerFnr = SM_FNR,
                orgnummer = null,
            )

            coEvery { pdfgenClient.getMerVeiledningPdfForDigitalePilotBrukere(SM_FNR) } returns ByteArray(1)

            coEvery { behandlendeEnhetClient.getBehandlendeEnhet(any()) } returns BehandlendeEnhet(
                enhetId = "0314",
                navn = "Test",
            )

            coEvery { dokarkivService.journalforDokument(any(), any(), any()) } returns journalpostId

            merveiledningVarselService.sendVarselTilArbeidstakerFromJob(
                arbeidstakerHendelse = hendelse,
                planlagtVarselUuid = planlagtVarselUuid
            )

            coVerify(exactly = 0) {
                senderFacade.sendBrevTilFysiskPrint(
                    journalpostUuid,
                    hendelse,
                    journalpostId,
                    DistibusjonsType.VIKTIG,
                )
            }

            coVerify(exactly = 1) {
                pdfgenClient.getMerVeiledningPdfForDigitalePilotBrukere(SM_FNR)
            }

            coVerify(exactly = 1) {
                dokarkivService.journalforDokument(
                    SM_FNR,
                    planlagtVarselUuid,
                    any(),
                )
            }

            coVerify(exactly = 1) {
                senderFacade.sendTilBrukernotifikasjoner(
                    uuid = any(),
                    mottakerFnr = SM_FNR,
                    content = BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT,
                    url = any(),
                    varselHendelse = hendelse,
                    varseltype = SenderFacade.InternalBrukernotifikasjonType.OPPGAVE
                )
            }
        }
    }
})
