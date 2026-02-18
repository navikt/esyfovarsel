package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.mocks.FNR_1
import no.nav.syfo.testutil.mocks.ORGNUMMER
import java.net.URI

class NyOppfolgingsplanVarselServiceSpek :
    DescribeSpec({
        val accessControlService = mockk<AccessControlService>()
        val dineSykmeldteHendelseKafkaProducer = mockk<DineSykmeldteHendelseKafkaProducer>()
        val dittSykefravaerMeldingKafkaProducer = mockk<DittSykefravaerMeldingKafkaProducer>()
        val brukernotifikasjonerService = mockk<BrukernotifikasjonerService>()
        val arbeidsgiverNotifikasjonService = mockk<ArbeidsgiverNotifikasjonService>()
        val fysiskBrevUtsendingService = mockk<FysiskBrevUtsendingService>()
        val embeddedDatabase = EmbeddedDatabase()
        val fakeNyOppfolgingsplanUrl = "http://localhost/syk/oppfolgingsplan/sykmeldt"

        val senderFacade =
            SenderFacade(
                dineSykmeldteHendelseKafkaProducer,
                dittSykefravaerMeldingKafkaProducer,
                brukernotifikasjonerService,
                arbeidsgiverNotifikasjonService,
                fysiskBrevUtsendingService,
                embeddedDatabase,
            )

        val service =
            NyOppfolgingsplanVarselService(
                senderFacade,
                accessControlService,
                fakeNyOppfolgingsplanUrl,
            )

        describe("NyOppfolgingsplanVarselService") {
            it("Sender varsel til sykmeldte om ferdigstilt oppfolgingsplan, som lenker til ny oppfølgingsplan url") {
                coEvery { accessControlService.canUserBeNotifiedByEmailOrSMS(FNR_1) } returns true

                val hendelse =
                    ArbeidstakerHendelse(
                        type = HendelseType.SM_OPPFOLGINGSPLAN_OPPRETTET,
                        ferdigstill = false,
                        data = null,
                        arbeidstakerFnr = FNR_1,
                        orgnummer = ORGNUMMER,
                    )

                service.sendVarselTilArbeidstaker(hendelse)

                verify(exactly = 1) {
                    brukernotifikasjonerService.sendBrukernotifikasjonVarsel(
                        any(),
                        FNR_1,
                        any(),
                        URI("$fakeNyOppfolgingsplanUrl/sykmeldt").toURL(),
                        any(),
                        true,
                    )
                }
            }
        }
    })
