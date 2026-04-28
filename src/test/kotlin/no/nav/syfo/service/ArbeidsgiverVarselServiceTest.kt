package no.nav.syfo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverNotifikasjonTilAltinnRessursHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import java.util.UUID

class ArbeidsgiverVarselServiceTest :
    DescribeSpec({
        val service = ArbeidsgiverVarselService()
        val objectMapper = createObjectMapper()

        val grunnHendelse =
            ArbeidsgiverNotifikasjonTilAltinnRessursHendelse(
                type = HendelseType.AG_VARSEL_ALTINN_RESSURS,
                ferdigstill = false,
                data = null,
                orgnummer = "999888777",
                ressursId = "nav_syfo_dialogmote",
                ressursUrl = "https://www.altinn.no",
                arbeidstakerFnr = "012345678901",
                kilde = "tjeneste.type",
                eksternReferanseId = UUID.randomUUID().toString(),
            )

        describe("ArbeidsgiverVarselService stub") {
            it("tåler gyldig varselData uten exception") {
                val gyldigData =
                    objectMapper.readTree(
                        """{"notifikasjonInnhold":{"epostTittel":"Hei","epostBody":"Body","smsTekst":"SMS"}}""",
                    )

                val result = runCatching { service.sendVarselTilArbeidsgiver(grunnHendelse.copy(data = gyldigData)) }

                result.isSuccess shouldBe true
            }

            it("håndterer parse-feil i data strengt og kaster exception") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        service.sendVarselTilArbeidsgiver(grunnHendelse.copy(data = "ikke-json"))
                    }
                exception.message shouldContain "ArbeidsgiverHendelse har ugyldig format i feltet: data"
            }

            it("kaster exception ved manglende data-felt") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        service.sendVarselTilArbeidsgiver(grunnHendelse.copy(data = null))
                    }

                exception.message shouldContain "ArbeidsgiverHendelse mangler feltet: data"
            }

            it("kaster exception ved manglende notifikasjonInnhold i varselData") {
                val manglendeNotifikasjonInnhold =
                    objectMapper.readTree(
                        """{}""",
                    )

                val exception =
                    shouldThrow<IllegalArgumentException> {
                        service.sendVarselTilArbeidsgiver(
                            grunnHendelse.copy(data = manglendeNotifikasjonInnhold),
                        )
                    }

                exception.message shouldContain "ArbeidsgiverHendelse mangler feltet: data.notifikasjonInnhold"
            }

            it("kaster exception ved manglende obligatorisk felt i notifikasjonInnhold") {
                val manglendeEpostBody =
                    objectMapper.readTree(
                        """{"notifikasjonInnhold":{"epostTittel":"Hei","smsTekst":"SMS"}}""",
                    )

                val exception =
                    shouldThrow<IllegalArgumentException> {
                        service.sendVarselTilArbeidsgiver(
                            grunnHendelse.copy(data = manglendeEpostBody),
                        )
                    }

                exception.message shouldContain "ArbeidsgiverHendelse har ugyldig format i feltet: data.notifikasjonInnhold.epostBody"
            }
        }
    })
