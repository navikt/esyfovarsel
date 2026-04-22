package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.syfo.BRUKERNOTIFIKASJONER_OPPFOLGINGSPLANER_SYKMELDT_URL
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederRelasjon
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.narmesteLeder.Tilgang
import no.nav.syfo.consumer.pdl.Foedselsdato
import no.nav.syfo.consumer.pdl.HentPerson
import no.nav.syfo.consumer.pdl.HentPersonData
import no.nav.syfo.consumer.pdl.Navn
import no.nav.syfo.consumer.pdl.PdlClient
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakInput
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.mocks.FNR_1
import no.nav.syfo.testutil.mocks.FNR_2
import no.nav.syfo.testutil.mocks.ORGNUMMER
import org.amshove.kluent.shouldBeEqualTo
import java.net.URI
import java.util.UUID

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
        val fakeDinesykmeldteUrl = "http://localhost/dinesykmeldte"
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
                fakeDinesykmeldteUrl,
                narmesteLederService,
                pdlClient,
            )

        describe("OppfolgingsplanVarselServiceSpek") {
            justRun {
                brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }

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

            it("Oppfolgingsplan foresporsel should use dinesykmeldte url for arbeidsgivernotifikasjon and sak") {
                val narmesteLederId = "1234"
                val expectedUrl = "$fakeDinesykmeldteUrl/$narmesteLederId"
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonInput>()
                val sakInputSlot = slot<NySakInput>()

                coEvery { narmesteLederService.getNarmesteLederRelasjon(FNR_1, ORGNUMMER) } returns
                    NarmesteLederRelasjon(
                        narmesteLederId = narmesteLederId,
                        tilganger = listOf(Tilgang.SYKMELDING),
                        navn = "Test Lansen",
                        narmesteLederEpost = "test@test.no",
                    )
                coEvery { pdlClient.hentPerson(FNR_1) } returns
                    HentPersonData(
                        hentPerson =
                            HentPerson(
                                foedselsdato = listOf(Foedselsdato(foedselsdato = "1990-01-01")),
                                navn = listOf(Navn(fornavn = "Test", mellomnavn = null, etternavn = "Testesen")),
                            ),
                    )
                coEvery { arbeidsgiverNotifikasjonService.createNewSak(any()) } returns UUID.randomUUID().toString()
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(any<ArbeidsgiverNotifikasjonInput>()) } returns Unit

                val varselHendelse =
                    NarmesteLederHendelse(
                        type = HendelseType.NL_OPPFOLGINGSPLAN_FORESPORSEL,
                        ferdigstill = false,
                        data = null,
                        narmesteLederFnr = FNR_2,
                        arbeidstakerFnr = FNR_1,
                        orgnummer = ORGNUMMER,
                    )

                oppfolgingsplanVarselService.sendOppfolgingsplanForesporselVarselTilNarmesteLeder(varselHendelse)

                coVerify(exactly = 1) {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(notifikasjonInputSlot))
                }
                coVerify(exactly = 1) {
                    arbeidsgiverNotifikasjonService.createNewSak(capture(sakInputSlot))
                }

                notifikasjonInputSlot.captured.link shouldBeEqualTo expectedUrl
                sakInputSlot.captured.lenke shouldBeEqualTo expectedUrl
            }
        }
    })
