package no.nav.syfo.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.UrlEnv
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.syfosmregister.SykmeldingDTO
import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.*
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.*
import java.util.*

object SendVarselServiceTestSpek : Spek({

    val beskjedKafkaProducerMockk: BeskjedKafkaProducer = mockk(relaxed = true)
    val dineSykmeldteHendelseKafkaProducerMockk: DineSykmeldteHendelseKafkaProducer = mockk(relaxed = true)
    val accessControlServiceMockk: AccessControlService = mockk(relaxed = true)
    val urlEnvMockk: UrlEnv = mockk(relaxed = true)
    val arbeidsgiverNotifikasjonServiceMockk: ArbeidsgiverNotifikasjonService = mockk(relaxed = true)
    val merVeiledningVarselServiceMockk: MerVeiledningVarselService = mockk()
    val sykmeldingerConsumerMock: SykmeldingerConsumer = mockk(relaxed = true)
    val sykmeldingServiceMockk = SykmeldingService(sykmeldingerConsumerMock)

    val sendVarselService = SendVarselService(
        beskjedKafkaProducerMockk,
        dineSykmeldteHendelseKafkaProducerMockk,
        accessControlServiceMockk,
        urlEnvMockk,
        arbeidsgiverNotifikasjonServiceMockk,
        merVeiledningVarselServiceMockk,
        sykmeldingServiceMockk
    )

    val sykmeldtFnr = "01234567891"
    val sykmeldtFnr1 = "00000000000"
    val orgnummer = "999988877"

    describe("SendVarselServiceSpek") {
        beforeEachTest {
            every { accessControlServiceMockk.getUserAccessStatus(sykmeldtFnr) } returns UserAccessStatus(
                sykmeldtFnr,
                canUserBeDigitallyNotified = true,
                canUserBePhysicallyNotified = false
            )
            every { accessControlServiceMockk.getUserAccessStatus(sykmeldtFnr1) } returns UserAccessStatus(
                sykmeldtFnr1,
                canUserBeDigitallyNotified = true,
                canUserBePhysicallyNotified = false
            )
            every { urlEnvMockk.baseUrlSykInfo } returns "https://www-gcp.dev.nav.no/syk/info"
        }

        afterEachTest {
            clearAllMocks()
        }

        it("Should send aktivitetskrav-varsel to AG if sykmelding sendt AG") {
            coEvery { sykmeldingerConsumerMock.getSykmeldingerPaDato(any(), any()) } returns listOf(
                getSykmeldingDto(
                    perioder = getSykmeldingPerioder(isGradert = false),
                    sykmeldingStatus = getSykmeldingStatus(isSendt = true, orgnummer = orgnummer)
                )
            )

            runBlocking {
                sendVarselService.sendVarsel(
                    PPlanlagtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = sykmeldtFnr,
                        orgnummer = orgnummer,
                        aktorId = null,
                        type = VarselType.AKTIVITETSKRAV.name,
                        utsendingsdato = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).toLocalDate(),
                        opprettet = LocalDateTime.now().minusDays(30),
                        sistEndret = LocalDateTime.now().minusDays(30)
                    )
                )
            }

            verify(exactly = 1) { beskjedKafkaProducerMockk.sendBeskjed(sykmeldtFnr, any(), any(), any()) }
            verify(exactly = 1) { dineSykmeldteHendelseKafkaProducerMockk.sendVarsel(any()) }
            verify(exactly = 1) { arbeidsgiverNotifikasjonServiceMockk.sendNotifikasjon(any()) }
        }

        it("Should not send aktivitetskrav-varsel to AG if sykmelding not sendt AG") {
            coEvery { sykmeldingerConsumerMock.getSykmeldingerPaDato(any(), any()) } returns listOf(
                getSykmeldingDto(
                    perioder = getSykmeldingPerioder(isGradert = false),
                    sykmeldingStatus = getSykmeldingStatus(isSendt = false, orgnummer = orgnummer)
                )
            )

            runBlocking {
                sendVarselService.sendVarsel(
                    PPlanlagtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = sykmeldtFnr,
                        orgnummer = orgnummer,
                        aktorId = null,
                        type = VarselType.AKTIVITETSKRAV.name,
                        utsendingsdato = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).toLocalDate(),
                        opprettet = LocalDateTime.now().minusDays(30),
                        sistEndret = LocalDateTime.now().minusDays(30)
                    )
                )
            }

            verify(exactly = 1) { beskjedKafkaProducerMockk.sendBeskjed(sykmeldtFnr, any(), any(), any()) }
            verify(exactly = 0) { dineSykmeldteHendelseKafkaProducerMockk.sendVarsel(any()) }
            verify(exactly = 0) { arbeidsgiverNotifikasjonServiceMockk.sendNotifikasjon(any()) }
        }

        it("Should send mer-veiledning-varsel to SM if sykmelding is sendt AG") {
            coEvery { sykmeldingerConsumerMock.getSykmeldingerPaDato(any(), sykmeldtFnr1) } returns listOf(
                getSykmeldingDto(
                    perioder = getSykmeldingPerioder(isGradert = false),
                    sykmeldingStatus = getSykmeldingStatus(isSendt = true, orgnummer = orgnummer)
                )
            )

            runBlocking {
                sendVarselService.sendVarsel(
                    PPlanlagtVarsel(
                        uuid = UUID.randomUUID().toString(),
                        fnr = sykmeldtFnr1,
                        orgnummer = orgnummer,
                        aktorId = null,
                        type = VarselType.MER_VEILEDNING.name,
                        utsendingsdato = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).toLocalDate(),
                        opprettet = LocalDateTime.now(),
                        sistEndret = LocalDateTime.now()
                    )
                )
            }

            verify(exactly = 1) { beskjedKafkaProducerMockk.sendBeskjed(sykmeldtFnr1, any(), any(), any()) }
        }
    }
})

fun getSykmeldingDto(perioder: List<SykmeldingsperiodeDTO>, sykmeldingStatus: SykmeldingStatusDTO): SykmeldingDTO {
    return SykmeldingDTO(
        id = "1",
        utdypendeOpplysninger = emptyMap(),
        kontaktMedPasient = KontaktMedPasientDTO(null, null),
        sykmeldingsperioder = perioder,
        sykmeldingStatus = sykmeldingStatus,
        behandlingsutfall = BehandlingsutfallDTO(RegelStatusDTO.OK, emptyList()),
        behandler = BehandlerDTO(
            "fornavn", null, "etternavn",
            "123", "444", null, null,
            AdresseDTO(null, null, null, null, null), null
        ),
        behandletTidspunkt = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)),
        mottattTidspunkt = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)),
        skjermesForPasient = false,
        medisinskVurdering = null,
        meldingTilNAV = null,
        prognose = null,
        arbeidsgiver = null,
        tiltakNAV = null,
        syketilfelleStartDato = null,
        tiltakArbeidsplassen = null,
        navnFastlege = null,
        meldingTilArbeidsgiver = null,
        legekontorOrgnummer = null,
        andreTiltak = null,
        egenmeldt = false,
        harRedusertArbeidsgiverperiode = false,
        papirsykmelding = false,
        merknader = null
    )
}

fun getSykmeldingPerioder(isGradert: Boolean): List<SykmeldingsperiodeDTO> {
    val gradertStatus: GradertDTO? = if (isGradert) GradertDTO(50, false) else null

    return listOf(
        SykmeldingsperiodeDTO(
            LocalDate.now(),
            LocalDate.now(),
            gradertStatus,
            null,
            null,
            PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
            null,
            false
        )
    )
}

fun getSykmeldingStatus(isSendt: Boolean, orgnummer: String): SykmeldingStatusDTO {
    if (isSendt) {
        return SykmeldingStatusDTO(
            "SENDT",
            OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)),
            ArbeidsgiverStatusDTO(
                orgnummer = orgnummer,
                juridiskOrgnummer = null,
                orgNavn = "Reke"
            ),
            emptyList()
        )
    }

    return SykmeldingStatusDTO(
        "APEN",
        OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)),
        null,
        emptyList()
    )
}