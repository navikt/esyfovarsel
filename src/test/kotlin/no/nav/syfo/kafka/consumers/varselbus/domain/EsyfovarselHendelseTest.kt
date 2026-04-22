package no.nav.syfo.kafka.consumers.varselbus.domain

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.kafka.common.createObjectMapper

class EsyfovarselHendelseTest :
    DescribeSpec({
        val objectMapper = createObjectMapper()
        val arbeidsgiverHendelseJson =
            """
            {
              "@type": "ArbeidsgiverHendelse",
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
              "ressursUrl": "https://www.altinn.no"
            }
            """.trimIndent()

        describe("EsyfovarselHendelse") {
            it("deserialiserer arbeidsgiverhendelse med AG_VARSEL_ALTINN_RESSURS til korrekt type") {
                val hendelse: EsyfovarselHendelse = objectMapper.readValue(arbeidsgiverHendelseJson)
                hendelse.data = objectMapper.readTree(arbeidsgiverHendelseJson)["data"]
                val varselData = requireNotNull(hendelse.data).toVarselData()

                (hendelse is ArbeidsgiverHendelse) shouldBe true
                hendelse.type shouldBe HendelseType.AG_VARSEL_ALTINN_RESSURS
                varselData.notifikasjonInnhold?.epostTittel shouldBe "Du har fått en ny melding"
                varselData.notifikasjonInnhold?.epostBody shouldBe "Les meldingen i Altinn"
                varselData.notifikasjonInnhold?.smsTekst shouldBe "Du har fått en ny melding i Altinn"
            }

            it("klassifiserer arbeidsgiverhendelse som ikke-arbeidstakerhendelse for mikrofrontend-guard") {
                val hendelse: EsyfovarselHendelse = objectMapper.readValue(arbeidsgiverHendelseJson)

                hendelse.isArbeidstakerHendelse() shouldBe false
            }
        }
    })
