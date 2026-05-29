package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.producer.arbeidsgivernotifikasjon.IArbeidsgiverNotifikasjonProdusent
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjonAltinnRessurs
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
    })
