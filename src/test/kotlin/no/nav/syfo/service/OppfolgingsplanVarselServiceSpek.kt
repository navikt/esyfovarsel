package no.nav.syfo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP
import no.nav.syfo.BRUKERNOTIFIKASJONER_OPPFOLGINGSPLANER_SYKMELDT_URL
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederRelasjon
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.narmesteLeder.Tilgang
import no.nav.syfo.consumer.pdl.Foedselsdato
import no.nav.syfo.consumer.pdl.HentPerson
import no.nav.syfo.consumer.pdl.HentPersonData
import no.nav.syfo.consumer.pdl.Navn
import no.nav.syfo.consumer.pdl.PdlClient
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.mocks.FNR_1
import no.nav.syfo.testutil.mocks.FNR_2
import no.nav.syfo.testutil.mocks.ORGNUMMER
import org.amshove.kluent.shouldBeEqualTo
import java.net.URI
import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.kafka.consumers.varselbus.domain.DineSykmeldteHendelseType

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
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonNarmestelederInput>()

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
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(any<ArbeidsgiverNotifikasjonNarmestelederInput>()) } returns true

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
                    arbeidsgiverNotifikasjonService.createNewSak(any())
                }

                val storedSak =
                    senderFacade.getPaagaaendeSak(
                        narmesteLederId = narmesteLederId,
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                    )

                notifikasjonInputSlot.captured.link shouldBeEqualTo expectedUrl
                storedSak?.lenke shouldBeEqualTo expectedUrl
            }

            it("Oppfolgingsplan varselbestilling should map payload texts to Dine Sykmeldte and arbeidsgivernotifikasjon") {
                val dineSykmeldteVarselSlot = slot<DineSykmeldteVarsel>()
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonNarmestelederInput>()
                justRun { dineSykmeldteHendelseKafkaProducer.sendVarsel(capture(dineSykmeldteVarselSlot)) }
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(notifikasjonInputSlot)) } returns true

                oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(
                    oppfolgingsplanVarselbestillingHendelse(),
                )
                dineSykmeldteVarselSlot.captured.tekst shouldBeEqualTo "Dine Sykmeldte-tekst"
                notifikasjonInputSlot.captured.messageText shouldBeEqualTo "Dine Sykmeldte-tekst"
                notifikasjonInputSlot.captured.messageText shouldBeEqualTo "Dine Sykmeldte-tekst"
                notifikasjonInputSlot.captured.epostTittel shouldBeEqualTo "E-posttittel"
                notifikasjonInputSlot.captured.epostHtmlBody shouldBeEqualTo "<p>E-postbody</p>"
                notifikasjonInputSlot.captured.meldingstype shouldBeEqualTo Meldingstype.BESKJED
            }

            it("Oppfolgingsplan varselbestilling should reject unsupported varselType") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(
                            oppfolgingsplanVarselbestillingHendelse(arbeidsgiverMeldingType = "KORT_BESKJED"),
                        )
                    }

                exception.message shouldBeEqualTo "Oppfølgingsplanvarsel har ugyldig data.varselType=KORT_BESKJED"
            }

            it("Oppfolgingsplan varselbestilling should reject missing notifikasjonInnhold") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(
                            oppfolgingsplanVarselbestillingHendelse(
                                rawData = """{"varselType":"BESKJED"}""",
                            ),
                        )
                    }

                exception.message shouldBeEqualTo "Oppfølgingsplanvarsel mangler feltet: data.notifikasjonInnhold"
            }

            it("Oppfolgingsplan varselbestilling should reject missing explicit varselTekst") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        oppfolgingsplanVarselService.sendVarselbestillingTilNarmesteLeder(
                            oppfolgingsplanVarselbestillingHendelse(
                                rawData =
                                    """
                                    {
                                      "arbeidsgiverMeldingType": "BESKJED",
                                      "notifikasjonInnhold": {
                                        "epostTittel": "E-posttittel",
                                        "epostBody": "<p>E-postbody</p>"
                                      }
                                    }
                                    """.trimIndent(),
                            ),
                        )
                    }

                exception.message shouldBeEqualTo
                    "Oppfølgingsplanvarsel mangler feltet: data.notifikasjonInnhold.varselTekst"
            }

            it("Oppfolgingsplan varselbestilling retry should reuse uuidEksternReferanse as notifikasjonsuuid") {
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonNarmestelederInput>()
                val eksternReferanse = UUID.randomUUID()
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(notifikasjonInputSlot)) } returns true

                val result =
                    oppfolgingsplanVarselService.resendVarselbestillingTilArbeidsgiverNotifikasjon(
                        PUtsendtVarselFeilet(
                            uuid = UUID.randomUUID().toString(),
                            uuidEksternReferanse = eksternReferanse.toString(),
                            arbeidstakerFnr = FNR_1,
                            narmesteLederFnr = FNR_2,
                            orgnummer = ORGNUMMER,
                            hendelsetypeNavn = HendelseType.NL_OPPFOLGINGSPLAN_VARSELBESTILLING.name,
                            arbeidsgivernotifikasjonMerkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                            brukernotifikasjonerMeldingType = null,
                            journalpostId = null,
                            kanal = "ARBEIDSGIVERNOTIFIKASJON",
                            feilmelding = "noe galt",
                            utsendtForsokTidspunkt = LocalDateTime.now(),
                            hendelseJson = createObjectMapper().writeValueAsString(
                                oppfolgingsplanVarselbestillingHendelse()
                            ),
                        ),
                    )

                result shouldBeEqualTo ArbeidsgiverVarselResendResult.RESENT
                notifikasjonInputSlot.captured.uuid shouldBeEqualTo eksternReferanse
            }

            it("Oppfolgingsplan varselbestilling retry should stay retryable when narmeste leder info mangler") {
                coEvery {
                    arbeidsgiverNotifikasjonService.sendNotifikasjon(any<ArbeidsgiverNotifikasjonNarmestelederInput>())
                } returns false

                val result =
                    oppfolgingsplanVarselService.resendVarselbestillingTilArbeidsgiverNotifikasjon(
                        PUtsendtVarselFeilet(
                            uuid = UUID.randomUUID().toString(),
                            uuidEksternReferanse = UUID.randomUUID().toString(),
                            arbeidstakerFnr = FNR_1,
                            narmesteLederFnr = FNR_2,
                            orgnummer = ORGNUMMER,
                            hendelsetypeNavn = HendelseType.NL_OPPFOLGINGSPLAN_VARSELBESTILLING.name,
                            arbeidsgivernotifikasjonMerkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                            brukernotifikasjonerMeldingType = null,
                            journalpostId = null,
                            kanal = "ARBEIDSGIVERNOTIFIKASJON",
                            feilmelding = "noe galt",
                            utsendtForsokTidspunkt = LocalDateTime.now(),
                            hendelseJson = createObjectMapper().writeValueAsString(
                                oppfolgingsplanVarselbestillingHendelse()
                            ),
                        ),
                    )

                result shouldBeEqualTo ArbeidsgiverVarselResendResult.RETRYABLE_FAILURE
            }

            it("Oppfolgingsplan varselbestilling retry should ignore extra smsTekst in stored hendelseJson") {
                val notifikasjonInputSlot = slot<ArbeidsgiverNotifikasjonNarmestelederInput>()
                coEvery { arbeidsgiverNotifikasjonService.sendNotifikasjon(capture(notifikasjonInputSlot)) } returns true

                val result =
                    oppfolgingsplanVarselService.resendVarselbestillingTilArbeidsgiverNotifikasjon(
                        PUtsendtVarselFeilet(
                            uuid = UUID.randomUUID().toString(),
                            uuidEksternReferanse = UUID.randomUUID().toString(),
                            arbeidstakerFnr = FNR_1,
                            narmesteLederFnr = FNR_2,
                            orgnummer = ORGNUMMER,
                            hendelsetypeNavn = HendelseType.NL_OPPFOLGINGSPLAN_VARSELBESTILLING.name,
                            arbeidsgivernotifikasjonMerkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                            brukernotifikasjonerMeldingType = null,
                            journalpostId = null,
                            kanal = "ARBEIDSGIVERNOTIFIKASJON",
                            feilmelding = "noe galt",
                            utsendtForsokTidspunkt = LocalDateTime.now(),
                            hendelseJson =
                                """
                                {
                                  "@type": "NarmesteLederHendelse",
                                  "type": "NL_OPPFOLGINGSPLAN_VARSELBESTILLING",
                                  "ferdigstill": false,
                                  "narmesteLederFnr": "$FNR_2",
                                  "arbeidstakerFnr": "$FNR_1",
                                  "orgnummer": "$ORGNUMMER",
                                  "data": {
                                    "arbeidsgiverMeldingType": "BESKJED",
                                    "dineSykmeldteHendelseType": "OPPFOLGINGSPLAN_OPPRETTET",
                                    "notifikasjonInnhold": {
                                      "epostTittel": "E-posttittel",
                                      "epostBody": "<p>E-postbody</p>",
                                      "smsTekst": "Gammel sms-tekst",
                                      "varselTekst": "Dine Sykmeldte-tekst"
                                    }
                                  }
                                }
                                """.trimIndent(),
                        ),
                    )

                result shouldBeEqualTo ArbeidsgiverVarselResendResult.RESENT
                notifikasjonInputSlot.captured.messageText shouldBeEqualTo "Dine Sykmeldte-tekst"
            }
        }
    })

private fun oppfolgingsplanVarselbestillingHendelse(
    arbeidsgiverMeldingType: String? = Meldingstype.BESKJED.name,
    dineSykmeldteHendelseType: String? = DineSykmeldteHendelseType.OPPFOLGINGSPLAN_PAAMINNELSE.name,
    rawData: String? = null,
) = NarmesteLederHendelse(
    type = HendelseType.NL_OPPFOLGINGSPLAN_VARSELBESTILLING,
    ferdigstill = false,
    data =
        createObjectMapper().readTree(
            rawData ?: (
                """
                {
                 "arbeidsgiverMeldingType": ${arbeidsgiverMeldingType?.let { "\"$it\"" } ?: "null"},
                 "dineSykmeldteHendelseType": ${dineSykmeldteHendelseType?.let { "\"$it\"" } ?: "null"},
                 "notifikasjonInnhold": {
                   "epostTittel": "E-posttittel",
                   "epostBody": "<p>E-postbody</p>",
                   "varselTekst": "Dine Sykmeldte-tekst"
                 }
                }
                """.trimIndent()
                ),
        ),
    narmesteLederFnr = FNR_2,
    arbeidstakerFnr = FNR_1,
    orgnummer = ORGNUMMER,
)
