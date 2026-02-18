package no.nav.syfo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataJournalpost
import org.amshove.kluent.shouldBeEqualTo

class ManglendeMedvirkningVarselServiceTest :
    DescribeSpec({
        val senderFacade = mockk<SenderFacade>(relaxed = true)
        val manglendeMedvirkningVarselService = ManglendeMedvirkningVarselService(senderFacade)
        val journalpostId = "620049753"
        val journalpostUuid = "bda0b55a-df72-4888-a5a5-6bfa74cacafe"
        val hendelse =
            ArbeidstakerHendelse(
                type = HendelseType.SM_FORHANDSVARSEL_MANGLENDE_MEDVIRKNING,
                ferdigstill = false,
                data = varselData(journalpostId = journalpostId, journalpostUuid = journalpostUuid),
                arbeidstakerFnr = SM_FNR,
                orgnummer = null,
            )

        beforeTest {
            clearAllMocks()
        }

        describe("Varsel ang. manglende medvirkning") {
            it("Sender varsel til fysisk print") {
                manglendeMedvirkningVarselService.sendVarselTilArbeidstaker(hendelse)

                coVerify(exactly = 1) {
                    senderFacade.sendBrevTilFysiskPrint(
                        journalpostUuid,
                        hendelse,
                        journalpostId,
                        DistibusjonsType.VIKTIG,
                    )
                }
            }

            it("Får IOException dersom mangende journalpostid") {
                val feilendeHendelse =
                    hendelse.copy(
                        data =
                            VarselData(
                                journalpost = VarselDataJournalpost(uuid = "something", id = null),
                            ),
                    )

                val exception =
                    shouldThrow<IOException> {
                        manglendeMedvirkningVarselService.sendVarselTilArbeidstaker(feilendeHendelse)
                    }

                exception.message shouldBeEqualTo "ArbeidstakerHendelse har feil format"
            }
        }
    })
