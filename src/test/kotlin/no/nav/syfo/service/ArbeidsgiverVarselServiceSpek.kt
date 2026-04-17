package no.nav.syfo.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.toArbeidsgiverHendelse
import org.slf4j.LoggerFactory

class ArbeidsgiverVarselServiceSpek :
    DescribeSpec({
        val service = ArbeidsgiverVarselService()
        val logger = LoggerFactory.getLogger(ArbeidsgiverVarselService::class.qualifiedName) as Logger
        val objectMapper = createObjectMapper()

        lateinit var listAppender: ListAppender<ILoggingEvent>

        fun arbeidsgiverHendelseFraJson(json: String): ArbeidsgiverHendelse {
            val varselEvent: EsyfovarselHendelse = objectMapper.readValue(json)
            varselEvent.data = objectMapper.readTree(json)["data"]
            return varselEvent.toArbeidsgiverHendelse()
        }

        fun List<ILoggingEvent>.shouldNotContainSensitiveVerdier() {
            this.forEach { event ->
                event.formattedMessage shouldNotContain "bda0b55a-df72-4888-a5a5-6bfa74cacafe"
                event.formattedMessage shouldNotContain "620049753"
                event.formattedMessage shouldNotContain "12345678910"
                event.throwableProxy?.message?.shouldNotContain("bda0b55a-df72-4888-a5a5-6bfa74cacafe")
                event.throwableProxy?.message?.shouldNotContain("620049753")
                event.throwableProxy?.message?.shouldNotContain("12345678910")
            }
        }

        beforeTest {
            listAppender =
                ListAppender<ILoggingEvent>().apply {
                    start()
                }
            logger.addAppender(listAppender)
        }

        afterTest {
            logger.detachAppender(listAppender)
            listAppender.stop()
        }

        describe("ArbeidsgiverVarselService") {
            it("logger stubbet arbeidsgiverhendelse med bare trygge eksplisitte felter") {
                service.sendVarselTilArbeidsgiver(
                    arbeidsgiverHendelseFraJson(
                        """
                        {
                          "@type": "ArbeidsgiverHendelse",
                          "type": "AG_DIALOGMOTE_REFERAT",
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
                        """.trimIndent(),
                    ),
                )

                listAppender.list.size shouldBe 1

                val logMessage = listAppender.list.single().formattedMessage
                logMessage shouldContain "Stubbet arbeidsgivervarsel kalt"
                logMessage shouldContain "AG_DIALOGMOTE_REFERAT"
                logMessage shouldContain "999999999"
                logMessage shouldContain "urn:altinn:resource:dialogmote"
                logMessage shouldContain "https://www.nav.no/arbeidsgiver/dialogmote"
                listAppender.list.shouldNotContainSensitiveVerdier()
            }

            it("logger feil og fortsetter med nullverdier når altinnRessurs.id mangler i data") {
                service.sendVarselTilArbeidsgiver(
                    arbeidsgiverHendelseFraJson(
                        """
                        {
                          "@type": "ArbeidsgiverHendelse",
                          "type": "AG_DIALOGMOTE_REFERAT",
                          "ferdigstill": false,
                          "arbeidstakerFnr": "12345678910",
                          "data": {
                            "altinnRessurs": {
                              "url": "https://www.nav.no/arbeidsgiver/dialogmote"
                            },
                            "journalpost": {
                              "uuid": "bda0b55a-df72-4888-a5a5-6bfa74cacafe",
                              "id": "620049753"
                            }
                          },
                          "orgnummer": "999999999"
                        }
                        """.trimIndent(),
                    ),
                )

                listAppender.list.size shouldBe 2

                val errorLog = listAppender.list.first()
                val infoLog = listAppender.list.last()

                errorLog.formattedMessage shouldContain "Feil ved konvertering av ArbeidsgiverHendelse til VarselDataAltinnRessurs"
                errorLog.throwableProxy?.message shouldContain "ArbeidsgiverHendelse.data har feil format"

                infoLog.formattedMessage shouldContain "Stubbet arbeidsgivervarsel kalt"
                infoLog.formattedMessage shouldContain HendelseType.AG_DIALOGMOTE_REFERAT.name
                infoLog.formattedMessage shouldContain "999999999"
                infoLog.formattedMessage shouldContain "altinnRessursId=null"
                infoLog.formattedMessage shouldContain "altinnRessursUrl=null"
                listAppender.list.shouldNotContainSensitiveVerdier()
            }

            it("logger feil og fortsetter med nullverdier når altinnRessurs.url mangler i data") {
                service.sendVarselTilArbeidsgiver(
                    arbeidsgiverHendelseFraJson(
                        """
                        {
                          "@type": "ArbeidsgiverHendelse",
                          "type": "AG_DIALOGMOTE_REFERAT",
                          "ferdigstill": false,
                          "arbeidstakerFnr": "12345678910",
                          "data": {
                            "altinnRessurs": {
                              "id": "urn:altinn:resource:dialogmote"
                            },
                            "journalpost": {
                              "uuid": "bda0b55a-df72-4888-a5a5-6bfa74cacafe",
                              "id": "620049753"
                            }
                          },
                          "orgnummer": "999999999"
                        }
                        """.trimIndent(),
                    ),
                )

                listAppender.list.size shouldBe 2

                val errorLog = listAppender.list.first()
                val infoLog = listAppender.list.last()

                errorLog.formattedMessage shouldContain "Feil ved konvertering av ArbeidsgiverHendelse til VarselDataAltinnRessurs"
                errorLog.throwableProxy?.message shouldContain "ArbeidsgiverHendelse.data har feil format"

                infoLog.formattedMessage shouldContain "Stubbet arbeidsgivervarsel kalt"
                infoLog.formattedMessage shouldContain HendelseType.AG_DIALOGMOTE_REFERAT.name
                infoLog.formattedMessage shouldContain "999999999"
                infoLog.formattedMessage shouldContain "altinnRessursId=null"
                infoLog.formattedMessage shouldContain "altinnRessursUrl=null"
                listAppender.list.shouldNotContainSensitiveVerdier()
            }
        }
    })
