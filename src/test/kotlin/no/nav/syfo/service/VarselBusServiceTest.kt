package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType

class VarselBusServiceTest :
    DescribeSpec({
        val senderFacade = mockk<SenderFacade>(relaxed = true)
        val arbeidsgiverVarselService = mockk<ArbeidsgiverVarselService>(relaxed = true)
        val motebehovVarselService = mockk<MotebehovVarselService>(relaxed = true)
        val oppfolgingsplanVarselService = mockk<OppfolgingsplanVarselService>(relaxed = true)
        val nyOppfolgingsplanVarselService = mockk<NyOppfolgingsplanVarselService>(relaxed = true)
        val dialogmoteInnkallingSykmeldtVarselService = mockk<DialogmoteInnkallingSykmeldtVarselService>(relaxed = true)
        val dialogmoteInnkallingNarmesteLederVarselService = mockk<DialogmoteInnkallingNarmesteLederVarselService>(relaxed = true)
        val aktivitetspliktForhandsvarselService = mockk<AktivitetspliktForhandsvarselService>(relaxed = true)
        val arbeidsuforhetForhandsvarselService = mockk<ArbeidsuforhetForhandsvarselService>(relaxed = true)
        val mikrofrontendService = mockk<no.nav.syfo.service.microfrontend.MikrofrontendService>(relaxed = true)
        val friskmeldingTilArbeidsformidlingVedtakService = mockk<FriskmeldingTilArbeidsformidlingVedtakService>(relaxed = true)
        val manglendeMedvirkningVarselService = mockk<ManglendeMedvirkningVarselService>(relaxed = true)
        val merVeiledningVarselService = mockk<MerVeiledningVarselService>(relaxed = true)
        val kartleggingssporsmalVarselService = mockk<KartleggingssporsmalVarselService>(relaxed = true)

        val varselBusService =
            VarselBusService(
                senderFacade = senderFacade,
                arbeidsgiverVarselService = arbeidsgiverVarselService,
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
            )

        val arbeidsgiverHendelse =
            ArbeidsgiverHendelse(
                type = HendelseType.AG_VARSEL_ALTINN_RESSURS,
                ferdigstill = false,
                data = """{"notifikasjonInnhold":{"epostTittel":"Tittel","epostBody":"Body","smsTekst":"SMS"}}""",
                orgnummer = "999888777",
                ressursId = "nav_syfo_dialogmote",
                ressursUrl = "https://www.altinn.no",
            )

        beforeTest {
            clearAllMocks()
        }

        describe("VarselBusService for arbeidsgiverhendelser") {
            it("ruter AG_VARSEL_ALTINN_RESSURS til ArbeidsgiverVarselService") {
                varselBusService.processVarselHendelse(arbeidsgiverHendelse)

                coVerify(exactly = 1) { arbeidsgiverVarselService.sendVarselTilArbeidsgiver(arbeidsgiverHendelse) }
                verify(exactly = 0) { mikrofrontendService.updateMikrofrontendForUserByHendelse(any()) }
            }

            it("ferdigstiller ikke arbeidsgiverhendelser i arbeidstaker/narmeste-leder-spor") {
                varselBusService.ferdigstillVarsel(arbeidsgiverHendelse.copy(ferdigstill = true))

                coVerify(exactly = 0) { senderFacade.ferdigstillArbeidstakerVarsler(any()) }
                coVerify(exactly = 0) { senderFacade.ferdigstillNarmesteLederVarsler(any()) }
            }
        }
    })
