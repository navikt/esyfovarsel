package no.nav.syfo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataJournalpost
import org.amshove.kluent.shouldBeEqualTo
import java.io.IOException

const val SM_FNR = "123456789"

class AktivitetskravVarselServiceTest : DescribeSpec({
    val accessControlService = mockk<AccessControlService>()
    val senderFacade = mockk<SenderFacade>(relaxed = true)
    val aktivitetspliktForhandsvarselVarselService = AktivitetspliktForhandsvarselVarselService(senderFacade, accessControlService, "http://dokumentarkivOppfolgingDocumetntsPageUrl", true)

    beforeTest {
        clearAllMocks()
    }

    describe("Forhåndsvarsel om stans av sykepenger") {
        it("Sender digital forhåndsvarsel til bruker hvis hen kan bli varslet digitalt") {
            val forhandsvarselEvent = createForhandsvarselHendelse()

            every { accessControlService.getUserAccessStatus(SM_FNR) } returns UserAccessStatus(
                SM_FNR,
                canUserBeDigitallyNotified = true,
            )

            aktivitetspliktForhandsvarselVarselService.sendVarselTilArbeidstaker(forhandsvarselEvent)

            verify(exactly = 0) {
                senderFacade.sendBrevTilFysiskPrint(
                    any(),
                    forhandsvarselEvent,
                    any(),
                    DistibusjonsType.VIKTIG,
                )
            }
            verify(exactly = 1) {
                senderFacade.sendTilBrukernotifikasjoner(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    true,
                )
            }
        }

        it("Får IOException dersom feil datatype") {
            val forhandsvarselEvent = createForhandsvarselHendelse()
            forhandsvarselEvent.data = "hei"

            every { accessControlService.getUserAccessStatus(SM_FNR) } returns UserAccessStatus(
                SM_FNR,
                canUserBeDigitallyNotified = true,
            )

            val exception = shouldThrow<IOException> {
                aktivitetspliktForhandsvarselVarselService.sendVarselTilArbeidstaker(forhandsvarselEvent)
            }

            exception.message shouldBeEqualTo "ArbeidstakerHendelse har feil format"
        }

        it("Får IOException dersom mangende journalpostid") {
            val forhandsvarselEvent = createForhandsvarselHendelse()
            forhandsvarselEvent.data = VarselData(journalpost = VarselDataJournalpost(uuid = "something", id = null))

            every { accessControlService.getUserAccessStatus(SM_FNR) } returns UserAccessStatus(
                SM_FNR,
                canUserBeDigitallyNotified = true,
            )

            val exception = shouldThrow<IOException> {
                aktivitetspliktForhandsvarselVarselService.sendVarselTilArbeidstaker(forhandsvarselEvent)
            }

            exception.message shouldBeEqualTo "ArbeidstakerHendelse har feil format"
        }

        it("Tester deserialisering av varseldata") {
            val objectMapper = createObjectMapper()

            every { accessControlService.getUserAccessStatus(any()) } returns UserAccessStatus(
                SM_FNR,
                canUserBeDigitallyNotified = false,
            )

            val jsondata: String = """
                {
                	"@type": "ArbeidstakerHendelse",
                	"type": "SM_AKTIVITETSPLIKT_STATUS_FORHANDSVARSEL",
                	"data": {
                		"journalpost": {
                			"uuid": "bda0b55a-df72-4888-a5a5-6bfa74cacafe",
                			"id": "620049753"
                		}
                	},
                	"arbeidstakerFnr": "***********",
                	"orgnummer": null
                }
            """

            val arbeidstakerHendelse = objectMapper.readValue(jsondata, ArbeidstakerHendelse::class.java)
            arbeidstakerHendelse.data = objectMapper.readTree(jsondata)["data"]

            aktivitetspliktForhandsvarselVarselService.sendVarselTilArbeidstaker(arbeidstakerHendelse)

            verify(exactly = 1) {
                senderFacade.sendBrevTilFysiskPrint(
                    any(),
                    arbeidstakerHendelse,
                    any(),
                    DistibusjonsType.VIKTIG,
                )
            }
        }
    }
})

private fun createForhandsvarselHendelse(): ArbeidstakerHendelse {
    return ArbeidstakerHendelse(
        type = HendelseType.SM_AKTIVITETSPLIKT_STATUS_FORHANDSVARSEL,
        false,
        varselData(journalpostId = "620049753", journalpostUuid = "bda0b55a-df72-4888-a5a5-6bfa74cacafe"),
        SM_FNR,
        null,
    )
}
