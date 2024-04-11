package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import no.nav.syfo.db.arbeidstakerFnr2
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.storeFodselsdato
import no.nav.syfo.db.storeSpleisUtbetaling
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.kafka.consumers.utbetaling.domain.UTBETALING_UTBETALT
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingSpleis
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class MerVeiledningVarselFinderSpek : DescribeSpec({

    val embeddedDatabase = EmbeddedDatabase()
    val sykmeldingerConsumerMock: SykmeldingerConsumer = mockk(relaxed = true)
    val sykmeldingServiceMockk = SykmeldingService(sykmeldingerConsumerMock)
    val pdlConsumerMockk: PdlConsumer = mockk(relaxed = true)
    val merVeiledningVarselFinder =
        MerVeiledningVarselFinder(embeddedDatabase, sykmeldingServiceMockk, pdlConsumerMockk)

    val spleisUtbetalingWhichResultsToVarsel = UtbetalingSpleis(
        fødselsnummer = arbeidstakerFnr1,
        organisasjonsnummer = "234",
        event = UTBETALING_UTBETALT,
        type = "UTBETALING",
        foreløpigBeregnetSluttPåSykepenger = LocalDate.now().plusDays(15),
        forbrukteSykedager = 100,
        gjenståendeSykedager = 79,
        stønadsdager = 10,
        antallVedtak = 4,
        fom = LocalDate.now().minusDays(50),
        tom = LocalDate.now().minusDays(3),
        utbetalingId = UUID.randomUUID().toString(),
        korrelasjonsId = UUID.randomUUID().toString(),
    )
    val spleisUtbetalingWhichResultsToVarsel2 = UtbetalingSpleis(
        fødselsnummer = arbeidstakerFnr2,
        organisasjonsnummer = "234",
        event = UTBETALING_UTBETALT,
        type = "UTBETALING",
        foreløpigBeregnetSluttPåSykepenger = LocalDate.now().plusDays(15),
        forbrukteSykedager = 100,
        gjenståendeSykedager = 79,
        stønadsdager = 10,
        antallVedtak = 4,
        fom = LocalDate.now().minusDays(50),
        tom = LocalDate.now().minusDays(3),
        utbetalingId = UUID.randomUUID().toString(),
        korrelasjonsId = UUID.randomUUID().toString(),
    )

    describe("MerVeiledningVarselFinderSpek") {
        beforeTest {
            clearAllMocks()
            embeddedDatabase.connection.dropData()
        }

        it("Should send MER_VEILEDNING when it was not sent during past 3 months") {
            coEvery { pdlConsumerMockk.isBrukerYngreEnnGittMaxAlder(any(), any()) } returns true
            coEvery { sykmeldingServiceMockk.isPersonSykmeldtPaDato(LocalDate.now(), arbeidstakerFnr1) } returns true
            embeddedDatabase.storeUtsendtVarsel(getUtsendtVarselToStore(LocalDateTime.now().minusMonths(5)))
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetalingWhichResultsToVarsel)

            val varslerToSendToday = merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday()

            varslerToSendToday.size shouldBeEqualTo 1
        }

        it("Should not send MER_VEILEDNING when it was sent during past 3 months") {
            coEvery { pdlConsumerMockk.isBrukerYngreEnnGittMaxAlder(any(), any()) } returns true
            coEvery { sykmeldingServiceMockk.isPersonSykmeldtPaDato(LocalDate.now(), arbeidstakerFnr1) } returns true
            embeddedDatabase.storeUtsendtVarsel(getUtsendtVarselToStore(LocalDateTime.now().minusMonths(1)))
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetalingWhichResultsToVarsel)

            val varslerToSendToday = merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday()

            varslerToSendToday.size shouldBeEqualTo 0
        }

        it("Should not send MER_VEILEDNING when it was not sent during past 3 months, but user is not active sykmeldt") {
            coEvery { pdlConsumerMockk.isBrukerYngreEnnGittMaxAlder(any(), any()) } returns true
            coEvery { sykmeldingServiceMockk.isPersonSykmeldtPaDato(LocalDate.now(), arbeidstakerFnr1) } returns false
            embeddedDatabase.storeUtsendtVarsel(getUtsendtVarselToStore(LocalDateTime.now().minusMonths(1)))
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetalingWhichResultsToVarsel)

            val varslerToSendToday = merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday()

            varslerToSendToday.size shouldBeEqualTo 0
        }

        it("Should send MER_VEILEDNING when user is under 67") {
            coEvery { pdlConsumerMockk.isBrukerYngreEnnGittMaxAlder(any(), any()) } returns true
            coEvery { sykmeldingServiceMockk.isPersonSykmeldtPaDato(LocalDate.now(), arbeidstakerFnr1) } returns true
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetalingWhichResultsToVarsel)

            val varslerToSendToday = merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday()

            varslerToSendToday.size shouldBeEqualTo 1
        }

        it("Should not send MER_VEILEDNING when user is over 67") {
            coEvery { pdlConsumerMockk.isBrukerYngreEnnGittMaxAlder(any(), any()) } returns false
            coEvery { sykmeldingServiceMockk.isPersonSykmeldtPaDato(LocalDate.now(), arbeidstakerFnr1) } returns true
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetalingWhichResultsToVarsel)

            val varslerToSendToday = merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday()

            varslerToSendToday.size shouldBeEqualTo 0
        }

        it("Should call PDL if stored birthdate is null for a given fnr") {
            embeddedDatabase.storeFodselsdato(arbeidstakerFnr1, null)
            coEvery { sykmeldingServiceMockk.isPersonSykmeldtPaDato(LocalDate.now(), arbeidstakerFnr1) } returns true
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetalingWhichResultsToVarsel)

            merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday()

            coVerify(exactly = 1) { pdlConsumerMockk.isBrukerYngreEnnGittMaxAlder(arbeidstakerFnr1, 67) }
        }

        it("Should not call PDL if stored birthdate is not null") {
            embeddedDatabase.storeFodselsdato(arbeidstakerFnr2, "1986-01-01")
            coEvery { sykmeldingServiceMockk.isPersonSykmeldtPaDato(LocalDate.now(), arbeidstakerFnr2) } returns true
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetalingWhichResultsToVarsel2)

            coVerify(exactly = 0) { pdlConsumerMockk.isBrukerYngreEnnGittMaxAlder(arbeidstakerFnr1, 67) }
        }
    }
})

private fun getUtsendtVarselToStore(utsendtTidspunkt: LocalDateTime): PUtsendtVarsel {
    return PUtsendtVarsel(
        UUID.randomUUID().toString(),
        arbeidstakerFnr1,
        null,
        "",
        orgnummer,
        HendelseType.SM_MER_VEILEDNING.name,
        null,
        utsendtTidspunkt,
        null,
        null,
        null,
        null,
    )
}
