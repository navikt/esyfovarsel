package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.pdfgen.PdfgenConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldtStatus
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import java.time.*
import java.util.*

class SendVarselServiceTestSpek : DescribeSpec({
    val brukernotifikasjonKafkaProducerMockk: BrukernotifikasjonKafkaProducer = mockk(relaxed = true)
    val dineSykmeldteHendelseKafkaProducerMockk: DineSykmeldteHendelseKafkaProducer = mockk(relaxed = true)
    val dittSykefravaerMeldingKafkaProducerMockk: DittSykefravaerMeldingKafkaProducer = mockk(relaxed = true)
    val fysiskBrevUtsendingServiceMockk: FysiskBrevUtsendingService = mockk(relaxed = true)
    val accessControlServiceMockk: AccessControlService = mockk(relaxed = true)
    val urlEnvMockk: UrlEnv = mockk(relaxed = true)
    val databaseInterfaceMockk: DatabaseInterface = mockk(relaxed = true)
    val arbeidsgiverNotifikasjonServiceMockk: ArbeidsgiverNotifikasjonService = mockk(relaxed = true)
    val dokarkivServiceMockk: DokarkivService = mockk(relaxed = true)
    val sykmeldingerConsumerMock: SykmeldingerConsumer = mockk(relaxed = true)
    val merVeiledningVarselFinder = mockk<MerVeiledningVarselFinder>(relaxed = true)
    val brukernotifikasjonerServiceMockk =
        BrukernotifikasjonerService(brukernotifikasjonKafkaProducerMockk)
    val pdfgenConsumerMockk: PdfgenConsumer = mockk(relaxed = true)

    val senderFacade =
        SenderFacade(
            dineSykmeldteHendelseKafkaProducerMockk,
            dittSykefravaerMeldingKafkaProducerMockk,
            brukernotifikasjonerServiceMockk,
            arbeidsgiverNotifikasjonServiceMockk,
            fysiskBrevUtsendingServiceMockk,
            databaseInterfaceMockk,
        )
    val merVeiledningVarselServiceMockk = MerVeiledningVarselService(
        senderFacade,
        urlEnvMockk,
        pdfgenConsumerMockk,
        dokarkivServiceMockk,
    )
    val sendVarselService = SendVarselService(
        accessControlServiceMockk,
        urlEnvMockk,
        merVeiledningVarselServiceMockk,
        merVeiledningVarselFinder,
    )
    val sykmeldtFnr = "01234567891"
    val orgnummer = "999988877"

    describe("SendVarselServiceSpek") {
        beforeTest {
            every { accessControlServiceMockk.getUserAccessStatus(sykmeldtFnr) } returns UserAccessStatus(
                sykmeldtFnr,
                canUserBeDigitallyNotified = true,
            )

            every { urlEnvMockk.baseUrlSykInfo } returns "https://www-gcp.dev.nav.no/syk/info"
        }

        afterTest {
            clearAllMocks()
        }

        it("Should send mer-veiledning-varsel to SM if sykmelding is sendt AG") {
            coEvery { sykmeldingerConsumerMock.getSykmeldtStatusPaDato(any(), sykmeldtFnr) } returns
                    SykmeldtStatus(
                        true,
                        true,
                        LocalDate.now(),
                        LocalDate.now(),
                    )

            coEvery { merVeiledningVarselFinder.isBrukerYngreEnn67Ar(sykmeldtFnr) } returns true

            runBlocking {
                sendVarselService.sendVarsel(
                    PPlanlagtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = sykmeldtFnr,
                        orgnummer = orgnummer,
                        aktorId = null,
                        type = VarselType.MER_VEILEDNING.name,
                        utsendingsdato = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).toLocalDate(),
                        opprettet = LocalDateTime.now(),
                        sistEndret = LocalDateTime.now(),
                    ),
                )
            }

            verify(exactly = 1) {
                brukernotifikasjonKafkaProducerMockk.sendBeskjed(
                    sykmeldtFnr,
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
        }
    }
})
