package no.nav.syfo.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.syfosmregister.SykmeldingerConsumer
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storeSpleisUtbetaling
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mocks.orgnummer
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

object MerVeiledningVarselFinderSpek : Spek({

    val embeddedDatabase by lazy { EmbeddedDatabase() }
    val sykmeldingerConsumerMock: SykmeldingerConsumer = mockk(relaxed = true)
    val sykmeldingServiceMockk = SykmeldingService(sykmeldingerConsumerMock)
    val merVeiledningVarselFinderMockk = MerVeiledningVarselFinder(embeddedDatabase, sykmeldingServiceMockk)

    val spleisUtbetalingWhichResultsToVarsel = UtbetalingUtbetalt(
        fødselsnummer = arbeidstakerFnr1,
        organisasjonsnummer = "234",
        event = "ubetaling_utbetalt",
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

    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    describe("VarselSenderServiceSpek") {
        afterEachTest {
            clearAllMocks()
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Should send MER_VEILEDNING when it was not sent during past 3 months") {
            coEvery { sykmeldingServiceMockk.isPersonSykmeldtPaDato(LocalDate.now(), arbeidstakerFnr1) } returns true
            embeddedDatabase.storeUtsendtVarsel(getUtsendtVarselToStore(LocalDateTime.now().minusMonths(5)))
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetalingWhichResultsToVarsel)

            val varslerToSendToday = runBlocking {
                merVeiledningVarselFinderMockk.findMerVeiledningVarslerToSendToday()
            }

            varslerToSendToday.size shouldBeEqualTo 1
        }

        it("Should not send MER_VEILEDNING when it was sent during past 3 months") {
            coEvery { sykmeldingServiceMockk.isPersonSykmeldtPaDato(LocalDate.now(), arbeidstakerFnr1) } returns true
            embeddedDatabase.storeUtsendtVarsel(getUtsendtVarselToStore(LocalDateTime.now().minusMonths(1)))
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetalingWhichResultsToVarsel)

            val varslerToSendToday = runBlocking {
                merVeiledningVarselFinderMockk.findMerVeiledningVarslerToSendToday()
            }

            varslerToSendToday.size shouldBeEqualTo 0
        }

        it("Should not send MER_VEILEDNING when it was not sent during past 3 months, but user is not active sykmeldt") {
            coEvery { sykmeldingServiceMockk.isPersonSykmeldtPaDato(LocalDate.now(), arbeidstakerFnr1) } returns false
            embeddedDatabase.storeUtsendtVarsel(getUtsendtVarselToStore(LocalDateTime.now().minusMonths(1)))
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetalingWhichResultsToVarsel)

            val varslerToSendToday = runBlocking {
                merVeiledningVarselFinderMockk.findMerVeiledningVarslerToSendToday()
            }

            varslerToSendToday.size shouldBeEqualTo 0
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
        VarselType.MER_VEILEDNING.name,
        null,
        utsendtTidspunkt,
        null,
        null
    )
}

