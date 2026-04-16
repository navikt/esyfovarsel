package no.nav.syfo.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataAltinnRessurs
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataJournalpost
import org.slf4j.LoggerFactory

class ArbeidsgiverVarselServiceSpek :
    DescribeSpec({
        val service = ArbeidsgiverVarselService()
        val logger = LoggerFactory.getLogger(ArbeidsgiverVarselService::class.qualifiedName) as Logger

        lateinit var listAppender: ListAppender<ILoggingEvent>

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
                    ArbeidsgiverHendelse(
                        type = HendelseType.AG_DIALOGMOTE_REFERAT,
                        ferdigstill = false,
                        arbeidstakerFnr = "12345678910",
                        data =
                            VarselData(
                                altinnRessurs =
                                    VarselDataAltinnRessurs(
                                        id = "urn:altinn:resource:dialogmote",
                                        url = "https://www.nav.no/arbeidsgiver/dialogmote",
                                    ),
                                journalpost =
                                    VarselDataJournalpost(
                                        uuid = "bda0b55a-df72-4888-a5a5-6bfa74cacafe",
                                        id = "620049753",
                                    ),
                            ),
                        orgnummer = "999999999",
                    ),
                )

                listAppender.list.size shouldBe 1

                val logMessage = listAppender.list.single().formattedMessage
                logMessage shouldContain "Stubbet arbeidsgivervarsel kalt"
                logMessage shouldContain "AG_DIALOGMOTE_REFERAT"
                logMessage shouldContain "999999999"
                logMessage shouldContain "urn:altinn:resource:dialogmote"
                logMessage shouldContain "https://www.nav.no/arbeidsgiver/dialogmote"
                logMessage shouldNotContain "bda0b55a-df72-4888-a5a5-6bfa74cacafe"
                logMessage shouldNotContain "620049753"
                logMessage shouldNotContain "12345678910"
            }

            it("feiler tydelig når arbeidsgiverhendelse har blank altinnRessurs.id") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        service.sendVarselTilArbeidsgiver(
                            ArbeidsgiverHendelse(
                                type = HendelseType.AG_DIALOGMOTE_REFERAT,
                                ferdigstill = false,
                                arbeidstakerFnr = "12345678910",
                                data =
                                    VarselData(
                                        altinnRessurs =
                                            VarselDataAltinnRessurs(
                                                id = "   ",
                                                url = "https://www.nav.no/arbeidsgiver/dialogmote",
                                            ),
                                        journalpost =
                                            VarselDataJournalpost(
                                                uuid = "bda0b55a-df72-4888-a5a5-6bfa74cacafe",
                                                id = "620049753",
                                            ),
                                    ),
                                orgnummer = "999999999",
                            ),
                        )
                    }

                exception.message shouldContain "altinnRessurs.id"
            }

            it("feiler tydelig når arbeidsgiverhendelse har blank altinnRessurs.url") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        service.sendVarselTilArbeidsgiver(
                            ArbeidsgiverHendelse(
                                type = HendelseType.AG_DIALOGMOTE_REFERAT,
                                ferdigstill = false,
                                arbeidstakerFnr = "12345678910",
                                data =
                                    VarselData(
                                        altinnRessurs =
                                            VarselDataAltinnRessurs(
                                                id = "urn:altinn:resource:dialogmote",
                                                url = "   ",
                                            ),
                                        journalpost =
                                            VarselDataJournalpost(
                                                uuid = "bda0b55a-df72-4888-a5a5-6bfa74cacafe",
                                                id = "620049753",
                                            ),
                                    ),
                                orgnummer = "999999999",
                            ),
                        )
                    }

                exception.message shouldContain "altinnRessurs.url"
            }
        }
    })
