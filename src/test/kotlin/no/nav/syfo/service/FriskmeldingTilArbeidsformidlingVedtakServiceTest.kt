package no.nav.syfo.service
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataJournalpost
import org.amshove.kluent.shouldBeEqualTo

class FriskmeldingTilArbeidsformidlingVedtakServiceTest :
    DescribeSpec({
        val senderFacade = mockk<SenderFacade>(relaxed = true)
        val friskmeldingTilArbeidsformidlingVedtakService = FriskmeldingTilArbeidsformidlingVedtakService(senderFacade)
        val journalpostId = "620049753"
        val journalpostUuid = "bda0b55a-df72-4888-a5a5-6bfa74cacafe"
        val hendelse =
            ArbeidstakerHendelse(
                type = HendelseType.SM_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING,
                ferdigstill = false,
                data = varselData(journalpostId = journalpostId, journalpostUuid = journalpostUuid),
                arbeidstakerFnr = SM_FNR,
                orgnummer = null,
            )

        beforeTest {
            clearAllMocks()
        }

        describe("Vedtak om friskmelding til arbeidsformidling") {
            it("Sender vedtak til fysisk print") {
                friskmeldingTilArbeidsformidlingVedtakService.sendVarselTilArbeidstaker(hendelse)

                coVerify(exactly = 1) {
                    senderFacade.sendBrevTilFysiskPrint(
                        journalpostUuid,
                        hendelse,
                        journalpostId,
                        DistibusjonsType.VIKTIG,
                    )
                }
            }

            it("Får IOException dersom mangende journalpostid") {
                val feilendeHendelse =
                    hendelse.copy(
                        data =
                            VarselData(
                                journalpost = VarselDataJournalpost(uuid = "something", id = null),
                            ),
                    )

                val exception =
                    shouldThrow<IOException> {
                        friskmeldingTilArbeidsformidlingVedtakService.sendVarselTilArbeidstaker(feilendeHendelse)
                    }

                exception.message shouldBeEqualTo "ArbeidstakerHendelse har feil format"
            }
        }
    })
