package no.nav.syfo.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.URL
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SenderFacadeSpek : Spek({
    describe("UtsendtVarselFeiletDAOSpek") {
        val senderFacade = mockk<SenderFacade>(relaxed = true)
        val brukernotifikasjonerService = mockk<BrukernotifikasjonerService>(relaxed = true)

        it("Skal lagre ikke utsendte varsler i egen tabell") {
            every { brukernotifikasjonerService.sendVarsel(any(), any(), any(), any(), any()) } throws Exception("qwqw")
            every {
                senderFacade.sendTilBrukernotifikasjoner(
                    any(), any(), any(), any(), any()
                )
            } answers { callOriginal() }

            senderFacade.sendTilBrukernotifikasjoner(
                "121212",
                "12121212121",
                "content",
                URL("https://nav.no"),
                ArbeidstakerHendelse(HendelseType.SM_DIALOGMOTE_INNKALT, data = null, "12121212121", "000"),
                meldingType = BrukernotifikasjonKafkaProducer.MeldingType.BESKJED
            )

            verify(exactly = 1) {
                senderFacade.lagreIkkeUtsendtArbeidstakerVarsel(
                    any(), any(), any(), any(), any()
                )
            }
            verify(exactly = 0) { senderFacade.lagreUtsendtArbeidstakerVarsel(any(), any(), any()) }

        }
    }
})