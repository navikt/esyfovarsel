package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataAltinnRessurs
import no.nav.syfo.service.microfrontend.MikrofrontendService

class VarselBusServiceTest :
    DescribeSpec({
        val senderFacade = mockk<SenderFacade>(relaxed = true)
        val motebehovVarselService = mockk<MotebehovVarselService>(relaxed = true)
        val oppfolgingsplanVarselService = mockk<OppfolgingsplanVarselService>(relaxed = true)
        val nyOppfolgingsplanVarselService = mockk<NyOppfolgingsplanVarselService>(relaxed = true)
        val dialogmoteInnkallingSykmeldtVarselService = mockk<DialogmoteInnkallingSykmeldtVarselService>(relaxed = true)
        val dialogmoteInnkallingNarmesteLederVarselService =
            mockk<DialogmoteInnkallingNarmesteLederVarselService>(relaxed = true)
        val aktivitetspliktForhandsvarselService = mockk<AktivitetspliktForhandsvarselService>(relaxed = true)
        val arbeidsuforhetForhandsvarselService = mockk<ArbeidsuforhetForhandsvarselService>(relaxed = true)
        val mikrofrontendService = mockk<MikrofrontendService>(relaxed = true)
        val friskmeldingTilArbeidsformidlingVedtakService =
            mockk<FriskmeldingTilArbeidsformidlingVedtakService>(relaxed = true)
        val manglendeMedvirkningVarselService = mockk<ManglendeMedvirkningVarselService>(relaxed = true)
        val merVeiledningVarselService = mockk<MerVeiledningVarselService>(relaxed = true)
        val kartleggingssporsmalVarselService = mockk<KartleggingssporsmalVarselService>(relaxed = true)
        val dialogmoteInnkallingArbeidsgiverVarselService = mockk<DialogmoteInnkallingArbeidsgiverVarselService>(relaxed = true)

        val varselBusService =
            VarselBusService(
                senderFacade = senderFacade,
                motebehovVarselService = motebehovVarselService,
                oppfolgingsplanVarselService = oppfolgingsplanVarselService,
                nyOppfolgingsplanVarselService = nyOppfolgingsplanVarselService,
                dialogmoteInnkallingSykmeldtVarselService = dialogmoteInnkallingSykmeldtVarselService,
                dialogmoteInnkallingNarmesteLederVarselService = dialogmoteInnkallingNarmesteLederVarselService,
                aktivitetspliktForhandsvarselService = aktivitetspliktForhandsvarselService,
                arbeidsuforhetForhandsvarselService = arbeidsuforhetForhandsvarselService,
                mikrofrontendService = mikrofrontendService,
                friskmeldingTilArbeidsformidlingVedtakService = friskmeldingTilArbeidsformidlingVedtakService,
                manglendeMedvirkningVarselService = manglendeMedvirkningVarselService,
                merVeiledningVarselService = merVeiledningVarselService,
                kartleggingssporsmalVarselService = kartleggingssporsmalVarselService,
                dialogmoteInnkallingArbeidsgiverVarselService = dialogmoteInnkallingArbeidsgiverVarselService,
            )

        beforeTest {
            clearAllMocks()
        }

        describe("VarselBusService") {
            describe("processVarselHendelse") {
                listOf(
                    HendelseType.AG_DIALOGMOTE_INNKALT,
                    HendelseType.AG_DIALOGMOTE_AVLYST,
                    HendelseType.AG_DIALOGMOTE_NYTT_TID_STED,
                    HendelseType.AG_DIALOGMOTE_REFERAT,
                ).forEach { hendelseType ->
                    it("delegates $hendelseType to arbeidsgiverVarselService") {
                        val hendelse = createArbeidsgiverHendelse(hendelseType = hendelseType, ferdigstill = false)

                        varselBusService.processVarselHendelse(hendelse)

                        verify(exactly = 1) { dialogmoteInnkallingArbeidsgiverVarselService.sendVarselTilArbeidsgiver(hendelse) }
                        coVerify(exactly = 0) { senderFacade.ferdigstillArbeidstakerVarsler(any()) }
                        coVerify(exactly = 0) { senderFacade.ferdigstillNarmesteLederVarsler(any()) }
                    }
                }

                it("does not ferdigstill arbeidsgiverhendelser via eksisterende senderFacade-flyt") {
                    val hendelse =
                        createArbeidsgiverHendelse(
                            hendelseType = HendelseType.AG_DIALOGMOTE_REFERAT,
                            ferdigstill = true,
                        )

                    varselBusService.processVarselHendelse(hendelse)

                    verify(exactly = 0) { dialogmoteInnkallingArbeidsgiverVarselService.sendVarselTilArbeidsgiver(any()) }
                    coVerify(exactly = 0) { senderFacade.ferdigstillArbeidstakerVarsler(any()) }
                    coVerify(exactly = 0) { senderFacade.ferdigstillNarmesteLederVarsler(any()) }
                }
            }
        }
    })

private fun createArbeidsgiverHendelse(
    hendelseType: HendelseType,
    ferdigstill: Boolean,
) = ArbeidsgiverHendelse(
    type = hendelseType,
    ferdigstill = ferdigstill,
    arbeidstakerFnr = "12345678910",
    data =
        VarselData(
            altinnRessurs =
                VarselDataAltinnRessurs(
                    id = "urn:altinn:resource:dialogmote",
                    url = "https://nav.no/dialogmote",
                ),
        ),
    orgnummer = "999999999",
)
