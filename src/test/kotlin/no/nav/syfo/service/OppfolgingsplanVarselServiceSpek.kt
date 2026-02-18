package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.BRUKERNOTIFIKASJONER_OPPFOLGINGSPLANER_SYKMELDT_URL
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.pdl.PdlClient
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.mocks.FNR_1
import no.nav.syfo.testutil.mocks.FNR_2
import no.nav.syfo.testutil.mocks.ORGNUMMER
import java.net.URI

class OppfolgingsplanVarselServiceSpek :
    DescribeSpec({
        val accessControlService = mockk<AccessControlService>()
        val dineSykmeldteHendelseKafkaProducer = mockk<DineSykmeldteHendelseKafkaProducer>()
        val dittSykefravaerMeldingKafkaProducer = mockk<DittSykefravaerMeldingKafkaProducer>()
        val brukernotifikasjonerService = mockk<BrukernotifikasjonerService>()
        val arbeidsgiverNotifikasjonService = mockk<ArbeidsgiverNotifikasjonService>()
        val fysiskBrevUtsendingService = mockk<FysiskBrevUtsendingService>()
        val embeddedDatabase = EmbeddedDatabase()
        val fakeOppfolgingsplanerUrl = "http://localhost/oppfolgingsplaner"
        val narmesteLederService = mockk<NarmesteLederService>()
        val pdlClient = mockk<PdlClient>()

        val senderFacade =
            SenderFacade(
                dineSykmeldteHendelseKafkaProducer,
                dittSykefravaerMeldingKafkaProducer,
                brukernotifikasjonerService,
                arbeidsgiverNotifikasjonService,
                fysiskBrevUtsendingService,
                embeddedDatabase,
            )
        val oppfolgingsplanVarselService =
            OppfolgingsplanVarselService(
                senderFacade,
                accessControlService,
                fakeOppfolgingsplanerUrl,
                narmesteLederService,
                pdlClient,
            )

        describe("OppfolgingsplanVarselServiceSpek") {
            justRun { brukernotifikasjonerService.sendBrukernotifikasjonVarsel(any(), any(), any(), any(), any(), any()) }

            it("Non-reserved users should be notified externally") {
                coEvery { accessControlService.canUserBeNotifiedByEmailOrSMS(FNR_1) } returns true
                val varselHendelse =
                    ArbeidstakerHendelse(
                        HendelseType.SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
                        false,
                        null,
                        FNR_1,
                        ORGNUMMER,
                    )
                oppfolgingsplanVarselService.sendVarselTilArbeidstaker(varselHendelse)
                verify(exactly = 1) {
                    brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                        any(),
                        FNR_1,
                        any(),
                        any(),
                        any(),
                        true,
                    )
                }
            }

            it("Reserved users only notified on 'Min side'") {
                coEvery { accessControlService.canUserBeNotifiedByEmailOrSMS(FNR_2) } returns false
                val varselHendelse =
                    ArbeidstakerHendelse(
                        HendelseType.SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING,
                        false,
                        null,
                        FNR_2,
                        ORGNUMMER,
                    )
                oppfolgingsplanVarselService.sendVarselTilArbeidstaker(varselHendelse)
                verify(exactly = 1) {
                    brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                        any(),
                        FNR_2,
                        any(),
                        URI(fakeOppfolgingsplanerUrl + BRUKERNOTIFIKASJONER_OPPFOLGINGSPLANER_SYKMELDT_URL).toURL(),
                        any(),
                        false,
                    )
                }
            }
        }
    })
