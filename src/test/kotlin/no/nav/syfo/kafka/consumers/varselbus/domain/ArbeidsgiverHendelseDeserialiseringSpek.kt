package no.nav.syfo.kafka.consumers.varselbus.domain

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.syfo.kafka.common.createObjectMapper

class ArbeidsgiverHendelseDeserialiseringSpek :
    DescribeSpec({
        val objectMapper = createObjectMapper()

        describe("ArbeidsgiverHendelse deserialisering") {
            it("deserialiserer arbeidsgiverhendelse til riktig subtype med altinnRessurs tilgjengelig i data") {
                val json =
                    """
                    {
                      "@type": "ArbeidsgiverHendelse",
                      "type": "AG_DIALOGMOTE_INNKALT",
                      "ferdigstill": false,
                      "arbeidstakerFnr": "12345678910",
                      "data": {
                        "altinnRessurs": {
                          "id": "urn:altinn:resource:dialogmote",
                          "url": "https://www.nav.no/arbeidsgiver/dialogmote"
                        },
                        "journalpost": {
                          "uuid": "bda0b55a-df72-4888-a5a5-6bfa74cacafe",
                          "id": "620049753"
                        }
                      },
                      "orgnummer": "999999999"
                    }
                    """.trimIndent()

                val hendelse = objectMapper.readValue<EsyfovarselHendelse>(json)

                val arbeidsgiverHendelse = hendelse.shouldBeTypeOf<ArbeidsgiverHendelse>()

                arbeidsgiverHendelse.type shouldBe HendelseType.AG_DIALOGMOTE_INNKALT
                arbeidsgiverHendelse.arbeidstakerFnr shouldBe "12345678910"
                arbeidsgiverHendelse.orgnummer shouldBe "999999999"
                arbeidsgiverHendelse.getArbeidsgiverAltinnRessurs() shouldBe
                    VarselDataAltinnRessurs(
                        id = "urn:altinn:resource:dialogmote",
                        url = "https://www.nav.no/arbeidsgiver/dialogmote",
                    )

                arbeidsgiverHendelse.data shouldBe
                    mapOf(
                        "altinnRessurs" to
                            mapOf(
                                "id" to "urn:altinn:resource:dialogmote",
                                "url" to "https://www.nav.no/arbeidsgiver/dialogmote",
                            ),
                        "journalpost" to
                            mapOf(
                                "uuid" to "bda0b55a-df72-4888-a5a5-6bfa74cacafe",
                                "id" to "620049753",
                            ),
                    )
            }

            it("feiler tydelig når arbeidstakerFnr mangler for arbeidsgiverhendelse") {
                val json =
                    """
                    {
                      "@type": "ArbeidsgiverHendelse",
                      "type": "AG_DIALOGMOTE_INNKALT",
                      "ferdigstill": false,
                      "data": {
                        "altinnRessurs": {
                          "id": "urn:altinn:resource:dialogmote",
                          "url": "https://www.nav.no/arbeidsgiver/dialogmote"
                        }
                      },
                      "orgnummer": "999999999"
                    }
                    """.trimIndent()

                val exception =
                    shouldThrow<Exception> {
                        objectMapper.readValue<EsyfovarselHendelse>(json)
                    }

                exception.message shouldContain "arbeidstakerFnr"
            }

            it("feiler tydelig når altinnRessurs.id mangler for arbeidsgiverhendelse") {
                val json =
                    """
                    {
                      "@type": "ArbeidsgiverHendelse",
                      "type": "AG_DIALOGMOTE_INNKALT",
                      "ferdigstill": false,
                      "arbeidstakerFnr": "12345678910",
                      "data": {
                        "altinnRessurs": {
                          "url": "https://www.nav.no/arbeidsgiver/dialogmote"
                        }
                      },
                      "orgnummer": "999999999"
                    }
                    """.trimIndent()

                val exception =
                    shouldThrow<IllegalArgumentException> {
                        objectMapper
                            .readValue<EsyfovarselHendelse>(json)
                            .shouldBeTypeOf<ArbeidsgiverHendelse>()
                            .getArbeidsgiverAltinnRessurs()
                    }

                exception.message shouldContain "altinnRessurs.id"
            }

            it("feiler tydelig når data mangler for arbeidsgiverhendelse") {
                val json =
                    """
                    {
                      "@type": "ArbeidsgiverHendelse",
                      "type": "AG_DIALOGMOTE_INNKALT",
                      "ferdigstill": false,
                      "arbeidstakerFnr": "12345678910",
                      "orgnummer": "999999999"
                    }
                    """.trimIndent()

                val exception =
                    shouldThrow<IllegalArgumentException> {
                        objectMapper
                            .readValue<EsyfovarselHendelse>(json)
                            .shouldBeTypeOf<ArbeidsgiverHendelse>()
                            .getArbeidsgiverAltinnRessurs()
                    }

                exception.message shouldContain "'data'"
            }

            it("feiler tydelig når altinnRessurs mangler i data for arbeidsgiverhendelse") {
                val json =
                    """
                    {
                      "@type": "ArbeidsgiverHendelse",
                      "type": "AG_DIALOGMOTE_INNKALT",
                      "ferdigstill": false,
                      "arbeidstakerFnr": "12345678910",
                      "data": {
                        "journalpost": {
                          "uuid": "bda0b55a-df72-4888-a5a5-6bfa74cacafe",
                          "id": "620049753"
                        }
                      },
                      "orgnummer": "999999999"
                    }
                    """.trimIndent()

                val exception =
                    shouldThrow<IllegalArgumentException> {
                        objectMapper
                            .readValue<EsyfovarselHendelse>(json)
                            .shouldBeTypeOf<ArbeidsgiverHendelse>()
                            .getArbeidsgiverAltinnRessurs()
                    }

                exception.message shouldContain "'altinnRessurs'"
            }

            it("feiler tydelig når altinnRessurs.url mangler for arbeidsgiverhendelse") {
                val json =
                    """
                    {
                      "@type": "ArbeidsgiverHendelse",
                      "type": "AG_DIALOGMOTE_INNKALT",
                      "ferdigstill": false,
                      "arbeidstakerFnr": "12345678910",
                      "data": {
                        "altinnRessurs": {
                          "id": "urn:altinn:resource:dialogmote"
                        }
                      },
                      "orgnummer": "999999999"
                    }
                    """.trimIndent()

                val exception =
                    shouldThrow<IllegalArgumentException> {
                        objectMapper
                            .readValue<EsyfovarselHendelse>(json)
                            .shouldBeTypeOf<ArbeidsgiverHendelse>()
                            .getArbeidsgiverAltinnRessurs()
                    }

                exception.message shouldContain "altinnRessurs.url"
            }
        }
    })
