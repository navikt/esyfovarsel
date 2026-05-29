package no.nav.syfo.kafka.consumers.varselbus.domain

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.kafka.common.createObjectMapper

class EsyfovarselHendelseTest :
    DescribeSpec({
        val objectMapper = createObjectMapper()
        val arbeidsgiverHendelseJson =
            """
            {
              "@type": "ArbeidsgiverNotifikasjonTilAltinnRessursHendelse",
              "type": "AG_VARSEL_ALTINN_RESSURS",
              "ferdigstill": false,
              "data": {
                "notifikasjonInnhold": {
                  "epostTittel": "Du har fått en ny melding",
                  "epostBody": "Les meldingen i Altinn",
                  "smsTekst": "Du har fått en ny melding i Altinn",
                  "varselTekst": "Du har fått en ny notifikasjon i Altinn."
                }
              },
              "orgnummer": "999888777",
              "ressursId": "nav_syfo_dialogmote",
              "ressursUrl": "https://www.altinn.no",
              "kilde": "dokumentporten.dialogmote",
              "arbeidstakerFnr": "012345678901",
              "eksternReferanseId": "123e4567-e89b-12d3-a456-426614174000"
            }
            """.trimIndent()

        val arbeidsgiverHendelseJsonUtenVarselTekst =
            """
            {
              "@type": "ArbeidsgiverNotifikasjonTilAltinnRessursHendelse",
              "type": "AG_VARSEL_ALTINN_RESSURS",
              "ferdigstill": false,
              "data": {
                "notifikasjonInnhold": {
                  "epostTittel": "Du har fått en ny melding",
                  "epostBody": "Les meldingen i Altinn",
                  "smsTekst": "Du har fått en ny melding i Altinn"
                }
              },
              "orgnummer": "999888777",
              "ressursId": "nav_syfo_dialogmote",
              "ressursUrl": "https://www.altinn.no",
              "kilde": "dokumentporten.dialogmote",
              "arbeidstakerFnr": "012345678901",
              "eksternReferanseId": "123e4567-e89b-12d3-a456-426614174000"
            }
            """.trimIndent()

        describe("EsyfovarselHendelse") {
            it("deserialiserer arbeidsgiverhendelse med AG_VARSEL_ALTINN_RESSURS til korrekt type") {
                val hendelse: EsyfovarselHendelse = objectMapper.readValue(arbeidsgiverHendelseJson)
                hendelse.data = objectMapper.readTree(arbeidsgiverHendelseJson)["data"]
                val varselData = requireNotNull(hendelse.data).toVarselData()

                (hendelse is ArbeidsgiverNotifikasjonTilAltinnRessursHendelse) shouldBe true
                hendelse.type shouldBe HendelseType.AG_VARSEL_ALTINN_RESSURS
                varselData.notifikasjonInnhold?.epostTittel shouldBe "Du har fått en ny melding"
                varselData.notifikasjonInnhold?.epostBody shouldBe "Les meldingen i Altinn"
                varselData.notifikasjonInnhold?.smsTekst shouldBe "Du har fått en ny melding i Altinn"
                varselData.notifikasjonInnhold?.varselTekst shouldBe "Du har fått en ny notifikasjon i Altinn."
            }

            it("klassifiserer arbeidsgiverhendelse som ikke-arbeidstakerhendelse for mikrofrontend-guard") {
                val hendelse: EsyfovarselHendelse = objectMapper.readValue(arbeidsgiverHendelseJson)

                hendelse.isArbeidstakerHendelse() shouldBe false
            }

            it("faller tilbake til smsTekst når varselTekst mangler i arbeidsgiverhendelse") {
                val hendelse: EsyfovarselHendelse = objectMapper.readValue(arbeidsgiverHendelseJsonUtenVarselTekst)
                hendelse.data = objectMapper.readTree(arbeidsgiverHendelseJsonUtenVarselTekst)["data"]
                val varselData = requireNotNull(hendelse.data).toVarselData()

                varselData.notifikasjonInnhold?.smsTekst shouldBe "Du har fått en ny melding i Altinn"
                varselData.notifikasjonInnhold?.varselTekst shouldBe "Du har fått en ny melding i Altinn"
            }

            it("feiler deserialisering når arbeidstakerFnr mangler for arbeidsgiverhendelse") {
                val jsonUtenArbeidstakerFnr =
                    """
                    {
                      "@type": "ArbeidsgiverNotifikasjonTilAltinnRessursHendelse",
                      "type": "AG_VARSEL_ALTINN_RESSURS",
                      "ferdigstill": false,
                      "data": {},
                      "orgnummer": "999888777",
                      "ressursId": "nav_syfo_dialogmote",
                      "ressursUrl": "https://www.altinn.no",
                      "kilde": "dokumentporten.dialogmote",
                      "eksternReferanseId": "123e4567-e89b-12d3-a456-426614174000"
                    }
                    """.trimIndent()

                shouldThrow<Exception> {
                    objectMapper.readValue<EsyfovarselHendelse>(jsonUtenArbeidstakerFnr)
                }
            }
        }
    })
