package no.nav.syfo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import org.apache.commons.cli.MissingArgumentException

const val SM_FNR = "123456789"
const val JOURNALPOST_UUID = "97b886fe-6beb-40df-af2b-04e504bc340c"
const val JOURNALPOST_ID = "1"

class AktivitetskravVarselServiceTest : DescribeSpec({
    val accessControlService = mockk<AccessControlService>()
    val senderFacade = mockk<SenderFacade>(relaxed = true)
    val aktivitetskravVarselService = AktivitetskravVarselService(senderFacade, accessControlService)

    beforeTest {
        clearAllMocks()
    }

    describe("Forhåndsvarsel om stans av sykepenger") {
        it("Sender fysisk brev til bruker dersom forhåndsvarsel om stans og ingen adressesperre") {
            val forhandsvarselEvent = createForhandsvarselHendelse()

            every { accessControlService.getUserAccessStatus(SM_FNR) } returns UserAccessStatus(
                SM_FNR,
                canUserBeDigitallyNotified = true,
                canUserBePhysicallyNotified = true
            )

            aktivitetskravVarselService.sendVarselTilArbeidstaker(forhandsvarselEvent)

            verify(exactly = 1) { senderFacade.sendBrevTilFysiskPrint(any(), forhandsvarselEvent, JOURNALPOST_ID) }
        }

        it("Sender ikke brev til bruker dersom adressesperre") {
            val forhandsvarselEvent = createForhandsvarselHendelse()

            every { accessControlService.getUserAccessStatus(SM_FNR) } returns UserAccessStatus(
                SM_FNR,
                canUserBeDigitallyNotified = true,
                canUserBePhysicallyNotified = false
            )

            aktivitetskravVarselService.sendVarselTilArbeidstaker(forhandsvarselEvent)

            verify(exactly = 0) { senderFacade.sendBrevTilFysiskPrint(any(), forhandsvarselEvent, JOURNALPOST_ID) }
        }

        it("Sender ikke brev til bruker dersom manglende journalpost") {
            val forhandsvarselEvent = createForhandsvarselHendelse()
            forhandsvarselEvent.data = null

            every { accessControlService.getUserAccessStatus(SM_FNR) } returns UserAccessStatus(
                SM_FNR,
                canUserBeDigitallyNotified = true,
                canUserBePhysicallyNotified = true
            )

            shouldThrow<MissingArgumentException> {
                aktivitetskravVarselService.sendVarselTilArbeidstaker(forhandsvarselEvent)
            }

            verify(exactly = 0) { senderFacade.sendBrevTilFysiskPrint(any(), forhandsvarselEvent, any()) }
        }
    }
})

private fun createForhandsvarselHendelse(): ArbeidstakerHendelse {
    return ArbeidstakerHendelse(
        HendelseType.SM_FORHANDSVARSEL_STANS,
        false,
        varselDataAktivitetskrav(JOURNALPOST_UUID, JOURNALPOST_ID),
        SM_FNR,
        null,
    )
}

private fun varselDataAktivitetskrav(journalpostUuid: String, journalpostId: String) =
    """
        {
            "uuid": "$journalpostUuid",
            "id": "$journalpostId"
        }
""".trimIndent()
