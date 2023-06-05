package no.nav.syfo.service

import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
import no.nav.syfo.db.arbeidstakerFnr1
import no.nav.syfo.db.orgnummer1
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer.MeldingType.OPPGAVE
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object MotebehovVarselServiceSpek : Spek({
    val senderFacade: SenderFacade = mockk(relaxed = true)
    val sykmeldingService: SykmeldingService = mockk(relaxed = true)
    val motebehovVarselService = MotebehovVarselService(senderFacade, "http://localhost", sykmeldingService)
    defaultTimeout = 20000L

    describe("MotebehovVarselServiceSpek") {
        afterEachTest {
            clearAllMocks()
        }

        val arbeidstakerHendelseSvarMotebehov = ArbeidstakerHendelse(
            type = HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV,
            ferdigstill = false,
            data = null,
            arbeidstakerFnr = arbeidstakerFnr1,
            orgnummer = orgnummer1,
        )

        it("sendVarselTilArbeidstaker should send melding to Ditt sykefravær") {
            motebehovVarselService.sendVarselTilArbeidstaker(arbeidstakerHendelseSvarMotebehov)
            verify(exactly = 1) {
                senderFacade.sendTilDittSykefravaer(
                    any(),
                    any(),
                )
            }
        }

        it("sendVarselTilArbeidstaker should send oppgave to brukernotifikasjoner") {
            motebehovVarselService.sendVarselTilArbeidstaker(arbeidstakerHendelseSvarMotebehov)
            verify(exactly = 1) {
                senderFacade.sendTilBrukernotifikasjoner(
                    any(),
                    arbeidstakerFnr1,
                    BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST,
                    any(),
                    arbeidstakerHendelseSvarMotebehov,
                    OPPGAVE,
                )
            }
        }
    }
})
