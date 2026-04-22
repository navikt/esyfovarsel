package no.nav.syfo.producer.arbeidsgivernotifikasjon

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.getTestEnv
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjonNarmesteLeder
import no.nav.syfo.testutil.mocks.FNR_1
import no.nav.syfo.testutil.mocks.FNR_2
import no.nav.syfo.testutil.mocks.MockServers
import no.nav.syfo.testutil.mocks.ORGNUMMER
import java.time.LocalDateTime
import java.util.UUID

class ArbeidsgiverNotifikasjonProdusentSpek :
    DescribeSpec({
        val testEnv = getTestEnv()
        val mockServers = MockServers(testEnv.urlEnv, testEnv.authEnv)
        val azureAdMockServer = mockServers.mockAADServer()
        val azureAdConsumer = AzureAdTokenConsumer(testEnv.authEnv)
        val arbeidsgiverNotifikasjonMockServer = mockServers.mockArbeidsgiverNotifikasjonServer()
        val arbeidsgiverNotifikasjonProdusent = ArbeidsgiverNotifikasjonProdusent(testEnv.urlEnv, azureAdConsumer)

        val arbeidsgiverNotifikasjon =
            ArbeidsgiverNotifikasjonNarmesteLeder(
                varselId = UUID.randomUUID().toString(),
                virksomhetsnummer = ORGNUMMER,
                url = "",
                narmesteLederFnr = FNR_1,
                ansattFnr = FNR_2,
                messageText = "hei",
                narmesteLederEpostadresse = "test@test.no",
                merkelapp = "Oppfølging",
                emailTitle = "Hei du",
                emailBody = "Body",
                hardDeleteDate = LocalDateTime.now().plusDays(1),
                grupperingsid = UUID.randomUUID().toString(),
            )
        val expectedId = "d69f8c4f-8d34-47b0-9539-d3c2e54115da"

        beforeSpec {
            azureAdMockServer.start()
            arbeidsgiverNotifikasjonMockServer.start()
        }

        afterSpec {
            azureAdMockServer.stop(1L, 10L)
            arbeidsgiverNotifikasjonMockServer.stop(1L, 10L)
        }

        describe("ArbeidsgiverNotifikasjonProdusentSpek") {
            it("Should send oppgave") {
                arbeidsgiverNotifikasjonProdusent.createNewOppgaveForArbeidsgiver(arbeidsgiverNotifikasjon) shouldBe expectedId
            }

            it("Should keep API error message when oppgave response has data null and errors") {
                val exception =
                    shouldThrow<RuntimeException> {
                        arbeidsgiverNotifikasjonProdusent.createNewOppgaveForArbeidsgiver(
                            arbeidsgiverNotifikasjon.copy(messageText = "force-error"),
                        )
                    }

                exception.message shouldBe
                    "Could not send task to arbeidsgiver. because of error: Simulert feil fra arbeidsgiver notifikasjon API, data was null"
            }

            it("Should send beskjed") {
                arbeidsgiverNotifikasjonProdusent.createNewBeskjedForArbeidsgiver(arbeidsgiverNotifikasjon) shouldBe expectedId
            }

            it("Should serialize hardDeleteDate as required ISO-8601 string") {
                val hardDeleteDate = LocalDateTime.of(2024, 1, 2, 3, 4, 5)
                val requestBody =
                    jacksonObjectMapper().writeValueAsString(
                        VariablesCreate(
                            eksternId = UUID.randomUUID().toString(),
                            virksomhetsnummer = ORGNUMMER,
                            lenke = "",
                            naermesteLederFnr = FNR_1,
                            ansattFnr = FNR_2,
                            merkelapp = "Oppfølging",
                            tekst = "hei",
                            epostadresse = "test@test.no",
                            epostTittel = "Hei du",
                            epostHtmlBody = "Body",
                            sendevindu = EpostSendevinduTypes.LOEPENDE,
                            hardDeleteDate = hardDeleteDate.formatAsISO8601DateTime(),
                            grupperingsid = UUID.randomUUID().toString(),
                        ),
                    )

                requestBody.contains("\"hardDeleteDate\":\"2024-01-02T03:04:05\"") shouldBe true
            }
        }
    })
