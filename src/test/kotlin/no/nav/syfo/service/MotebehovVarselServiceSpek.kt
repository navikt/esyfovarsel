package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
import no.nav.syfo.db.arbeidstakerFnr1
import no.nav.syfo.db.arbeidstakerFnr2
import no.nav.syfo.db.arbeidstakerFnr3
import no.nav.syfo.db.orgnummer1
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.OPPGAVE

class MotebehovVarselServiceSpek : DescribeSpec({
    val senderFacade: SenderFacade = mockk(relaxed = true)
    val sykmeldingService: SykmeldingService = mockk(relaxed = true)
    val accessControlService = mockk<AccessControlService>()
    val motebehovVarselService =
        MotebehovVarselService(senderFacade, accessControlService, sykmeldingService, "http://localhost")

    describe("MotebehovVarselServiceSpek") {
        beforeTest {
            clearAllMocks()
        }

        val arbeidstakerHendelseSvarMotebehov1 = ArbeidstakerHendelse(
            type = HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV,
            ferdigstill = false,
            data = null,
            arbeidstakerFnr = arbeidstakerFnr1,
            orgnummer = orgnummer1,
        )

        val arbeidstakerHendelseSvarMotebehov2 = arbeidstakerHendelseSvarMotebehov1
            .copy(arbeidstakerFnr = arbeidstakerFnr2)

        val arbeidstakerHendelseSvarMotebehov3 = arbeidstakerHendelseSvarMotebehov1
            .copy(arbeidstakerFnr = arbeidstakerFnr3)

        it("sendVarselTilArbeidstaker should send melding to Ditt sykefravær") {
            coEvery { accessControlService.canUserBeNotifiedByEmailOrSMS(any()) } returns true
            motebehovVarselService.sendVarselTilArbeidstaker(arbeidstakerHendelseSvarMotebehov1)
            verify(exactly = 1) {
                senderFacade.sendTilDittSykefravaer(
                    any(),
                    any(),
                )
            }
        }

        it("sendVarselTilArbeidstaker should send oppgave to brukernotifikasjoner") {
            coEvery { accessControlService.canUserBeNotifiedByEmailOrSMS(any()) } returns true
            motebehovVarselService.sendVarselTilArbeidstaker(arbeidstakerHendelseSvarMotebehov2)
            verify(exactly = 1) {
                senderFacade.sendTilBrukernotifikasjoner(
                    any(),
                    arbeidstakerFnr2,
                    BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST,
                    any(),
                    arbeidstakerHendelseSvarMotebehov2,
                    varseltype = OPPGAVE,
                    eksternVarsling = true,
                )
            }
        }

        it("Reserved users should not be notified digitally") {
            coEvery { accessControlService.canUserBeNotifiedByEmailOrSMS(any()) } returns false
            motebehovVarselService.sendVarselTilArbeidstaker(arbeidstakerHendelseSvarMotebehov3)
            verify(exactly = 1) {
                senderFacade.sendTilBrukernotifikasjoner(
                    any(),
                    arbeidstakerFnr3,
                    BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST,
                    any(),
                    arbeidstakerHendelseSvarMotebehov3,
                    OPPGAVE,
                    eksternVarsling = false,
                )
            }
        }
    }
})
