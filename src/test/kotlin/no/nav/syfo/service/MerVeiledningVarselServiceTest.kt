package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.getTestEnv
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType

class MerVeiledningVarselServiceTest :
    DescribeSpec({
        val senderFacade = mockk<SenderFacade>(relaxed = true)
        val accessControlService = mockk<AccessControlService>(relaxed = true)
        val testEnv = getTestEnv()
        val merveiledningVarselService =
            MerVeiledningVarselService(
                senderFacade = senderFacade,
                env = testEnv,
                accessControlService = accessControlService,
            )

        beforeTest {
            clearAllMocks()
        }

        describe("Varsel om mer veiledning") {
            it("Sender varsel til dokdistribusjon dersom reservert") {
                coEvery { accessControlService.getUserAccessStatus(any()) } returns
                    UserAccessStatus(
                        fnr = SM_FNR,
                        canUserBeDigitallyNotified = false,
                    )

                val journalpostId = "420049753"
                val journalpostUuid = "cda0b55a-df72-4888-a5a5-6bfa74cacafe"

                val hendelse =
                    ArbeidstakerHendelse(
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
                        arbeidstakerFnr = hendelse.arbeidstakerFnr,
                        orgnummer = hendelse.orgnummer,
                        hendelseType = hendelse.type.name,
                        varseltype = SenderFacade.InternalBrukernotifikasjonType.OPPGAVE,
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
                coEvery { accessControlService.getUserAccessStatus(any()) } returns
                    UserAccessStatus(
                        fnr = SM_FNR,
                        canUserBeDigitallyNotified = true,
                    )

                val journalpostId = "420049753"
                val journalpostUuid = "cda0b55a-df72-4888-a5a5-6bfa74cacafe"

                val hendelse =
                    ArbeidstakerHendelse(
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
                        arbeidstakerFnr = hendelse.arbeidstakerFnr,
                        orgnummer = hendelse.orgnummer,
                        hendelseType = hendelse.type.name,
                        varseltype = SenderFacade.InternalBrukernotifikasjonType.OPPGAVE,
                        dagerTilDeaktivering = 105,
                    )
                }
            }
        }
    })
