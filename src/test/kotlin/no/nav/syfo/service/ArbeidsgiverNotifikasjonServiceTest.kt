package no.nav.syfo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederRelasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.IArbeidsgiverNotifikasjonProdusent
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjonAltinnRessurs
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjonNarmesteLeder
import java.time.LocalDateTime
import java.util.UUID

class ArbeidsgiverNotifikasjonServiceTest :
    DescribeSpec({
        val arbeidsgiverNotifikasjonProdusent = mockk<IArbeidsgiverNotifikasjonProdusent>()
        val narmesteLederService = mockk<no.nav.syfo.consumer.narmesteLeder.NarmesteLederService>(relaxed = true)
        val service =
            ArbeidsgiverNotifikasjonService(
                arbeidsgiverNotifikasjonProdusent = arbeidsgiverNotifikasjonProdusent,
                narmesteLederService = narmesteLederService,
                dineSykmeldteUrl = "https://dinesykmeldte",
            )

        describe("sendNotifikasjon for Altinn-ressurs") {
            it("videresender varseltekst og smsTekst uten midlertidig splitting") {
                val input =
                    ArbeidsgiverNotifikasjonAltinnRessursInput(
                        uuid = UUID.randomUUID(),
                        virksomhetsnummer = "999888777",
                        merkelapp = "merkelapp",
                        messageText = "Første setning. Andre setning. Tredje setning.",
                        smsTekst = "Kort sms-tekst",
                        epostTittel = "Tittel",
                        epostHtmlBody = "<p>Body</p>",
                        hardDeleteDate = LocalDateTime.of(2030, 1, 1, 0, 0),
                        grupperingsid = UUID.randomUUID().toString(),
                        ressursId = "nav_syfo_dialogmote",
                        ressursUrl = "https://www.altinn.no",
                    )
                val capturedNotifikasjoner = mutableListOf<ArbeidsgiverNotifikasjon>()

                coEvery {
                    arbeidsgiverNotifikasjonProdusent.createNewBeskjedForArbeidsgiver(capture(capturedNotifikasjoner))
                } returns "notifikasjon-id"

                service.sendNotifikasjon(input)

                val actual = capturedNotifikasjoner.single() as ArbeidsgiverNotifikasjonAltinnRessurs
                actual.messageText shouldBe input.messageText
                actual.smsTekst shouldBe input.smsTekst
            }
        }

        describe("sendNotifikasjon for nærmeste leder") {
            it("videresender e-postvarsel for nærmeste leder") {
                val input =
                    ArbeidsgiverNotifikasjonNarmestelederInput(
                        uuid = UUID.randomUUID(),
                        virksomhetsnummer = "999888777",
                        narmesteLederFnr = "12345678910",
                        ansattFnr = "10987654321",
                        merkelapp = "merkelapp",
                        messageText = "Varseltekst",
                        epostTittel = "Tittel",
                        epostHtmlBody = "<p>Body</p>",
                        hardDeleteDate = LocalDateTime.of(2030, 1, 1, 0, 0),
                        grupperingsid = UUID.randomUUID().toString(),
                    )
                val capturedNotifikasjoner = mutableListOf<ArbeidsgiverNotifikasjon>()
                coEvery { narmesteLederService.getNarmesteLederRelasjon(input.ansattFnr, input.virksomhetsnummer) } returns
                    NarmesteLederRelasjon(
                        narmesteLederFnr = input.narmesteLederFnr,
                        narmesteLederEpost = "leder@test.no",
                        narmesteLederTelefonnummer = "99999999",
                        tilganger = emptyList(),
                        navn = "Leder",
                    )
                every { narmesteLederService.hasNarmesteLederInfo(any()) } returns true
                coEvery {
                    arbeidsgiverNotifikasjonProdusent.createNewBeskjedForArbeidsgiver(capture(capturedNotifikasjoner))
                } returns "notifikasjon-id"

                val result = service.sendNotifikasjon(input)

                result shouldBe true
                val actual = capturedNotifikasjoner.single() as ArbeidsgiverNotifikasjonNarmesteLeder
                actual.messageText shouldBe input.messageText
            }

            it("returnerer true når telefonnummer mangler for nærmeste leder") {
                val input =
                    ArbeidsgiverNotifikasjonNarmestelederInput(
                        uuid = UUID.randomUUID(),
                        virksomhetsnummer = "999888777",
                        narmesteLederFnr = "12345678910",
                        ansattFnr = "10987654321",
                        merkelapp = "merkelapp",
                        messageText = "Varseltekst",
                        epostTittel = "Tittel",
                        epostHtmlBody = "<p>Body</p>",
                        hardDeleteDate = LocalDateTime.of(2030, 1, 1, 0, 0),
                        grupperingsid = UUID.randomUUID().toString(),
                    )
                coEvery { narmesteLederService.getNarmesteLederRelasjon(input.ansattFnr, input.virksomhetsnummer) } returns
                    NarmesteLederRelasjon(
                        narmesteLederFnr = input.narmesteLederFnr,
                        narmesteLederEpost = "leder@test.no",
                        narmesteLederTelefonnummer = null,
                        tilganger = emptyList(),
                        navn = "Leder",
                    )
                every { narmesteLederService.hasNarmesteLederInfo(any()) } returns true
                coEvery {
                    arbeidsgiverNotifikasjonProdusent.createNewBeskjedForArbeidsgiver(any())
                } returns "notifikasjon-id"

                val result = service.sendNotifikasjon(input)

                result shouldBe true
            }

            it("kaster exception når produsent returnerer null ID") {
                val input =
                    ArbeidsgiverNotifikasjonNarmestelederInput(
                        uuid = UUID.randomUUID(),
                        virksomhetsnummer = "999888777",
                        narmesteLederFnr = "12345678910",
                        ansattFnr = "10987654321",
                        merkelapp = "merkelapp",
                        messageText = "Varseltekst",
                        epostTittel = "Tittel",
                        epostHtmlBody = "<p>Body</p>",
                        hardDeleteDate = LocalDateTime.of(2030, 1, 1, 0, 0),
                        grupperingsid = UUID.randomUUID().toString(),
                    )
                coEvery { narmesteLederService.getNarmesteLederRelasjon(input.ansattFnr, input.virksomhetsnummer) } returns
                    NarmesteLederRelasjon(
                        narmesteLederFnr = input.narmesteLederFnr,
                        narmesteLederEpost = "leder@test.no",
                        narmesteLederTelefonnummer = "99999999",
                        tilganger = emptyList(),
                        navn = "Leder",
                    )
                every { narmesteLederService.hasNarmesteLederInfo(any()) } returns true
                coEvery {
                    arbeidsgiverNotifikasjonProdusent.createNewBeskjedForArbeidsgiver(any())
                } returns null

                shouldThrow<IllegalStateException> {
                    service.sendNotifikasjon(input)
                }.message shouldBe
                    "ArbeidsgiverNotifikasjonProdusent returnerte null ID ved utsending til nærmeste leder"
            }
        }
    })
