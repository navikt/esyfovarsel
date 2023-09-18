package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataJournalpost

const val SM_FNR = "123456789"

class AktivitetskravVarselServiceTest : DescribeSpec({
    val accessControlService = mockk<AccessControlService>()
    val senderFacade = mockk<SenderFacade>(relaxed = true)
    val aktivitetskravVarselService = AktivitetskravVarselService(senderFacade, accessControlService)

    beforeTest {
        clearAllMocks()
    }

    describe("Forhåndsvarsel om stans av sykepenger") {
        it("Sender alltid fysisk brev for forhåndsvarsel") {
            val forhandsvarselEvent = createForhandsvarselHendelse()

            every { accessControlService.getUserAccessStatus(SM_FNR) } returns UserAccessStatus(
                SM_FNR,
                canUserBeDigitallyNotified = true,
            )

            aktivitetskravVarselService.sendVarselTilArbeidstaker(forhandsvarselEvent)

            verify(exactly = 1) { senderFacade.sendBrevTilFysiskPrint(any(), forhandsvarselEvent, any()) }
            verify(exactly = 0) {
                senderFacade.sendTilBrukernotifikasjoner(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    true
                )
            }
        }
    }
})

private fun createForhandsvarselHendelse(): ArbeidstakerHendelse {
    return ArbeidstakerHendelse(
        HendelseType.SM_FORHANDSVARSEL_STANS,
        false,
        VarselDataJournalpost(uuid = "97b886fe-6beb-40df-af2b-04e504bc340c", id = "1"),
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
