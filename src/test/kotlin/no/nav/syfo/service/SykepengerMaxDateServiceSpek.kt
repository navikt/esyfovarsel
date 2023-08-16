package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.consumer.pdl.PdlFoedsel
import no.nav.syfo.consumer.pdl.PdlHentPerson
import no.nav.syfo.consumer.pdl.PdlPerson
import no.nav.syfo.db.fetchSpleisUtbetalingByFnr
import no.nav.syfo.db.fetchSykepengerMaxDateByFnr
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

class SykepengerMaxDateServiceSpek : DescribeSpec({
    describe("SykepengerMaxDateService") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        val pdlConsumer = mockk<PdlConsumer>(relaxed = true)
        val sykepengerMaxDateService = SykepengerMaxDateService(embeddedDatabase, pdlConsumer)
        coEvery { pdlConsumer.hentPerson(any()) } returns PdlHentPerson(
            hentPerson = PdlPerson(
                adressebeskyttelse = null,
                navn = null,
                foedsel = listOf(PdlFoedsel("1986-01-01"))
            )
        )
        afterTest {
            embeddedDatabase.connection.dropData()
        }

        afterSpec {
            embeddedDatabase.stop()
        }

        it("Should store new dates") {
            val fiftyDaysFromNow = LocalDate.now().plusDays(50)

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = fiftyDaysFromNow,
                source = SykepengerMaxDateSource.SPLEIS
            )

            val storedMaxDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(fiftyDaysFromNow, storedMaxDate)
        }

        it("Should update existing date") {
            val fiftyDaysFromNow = LocalDate.now().plusDays(50)
            val fourtyDaysFromNow = LocalDate.now().plusDays(40)

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = fiftyDaysFromNow,
                source = SykepengerMaxDateSource.SPLEIS
            )

            val storedMaxDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(fiftyDaysFromNow, storedMaxDate)

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = fourtyDaysFromNow,
                source = SykepengerMaxDateSource.INFOTRYGD
            )

            val newStoredMaxDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(fourtyDaysFromNow, newStoredMaxDate)
        }

        it("Should delete dates older than today") {
            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = LocalDate.now().minusDays(20),
                source = SykepengerMaxDateSource.SPLEIS
            )

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = LocalDate.now().minusDays(19),
                source = SykepengerMaxDateSource.INFOTRYGD
            )

            val storedDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(null, storedDate)
        }

        it("Should delete maxdate if new date is null") {
            val fourtyDaysFromNow = LocalDate.now().plusDays(40)

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = fourtyDaysFromNow,
                source = SykepengerMaxDateSource.SPLEIS
            )

            val storedDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(fourtyDaysFromNow, storedDate)

            sykepengerMaxDateService.processNewMaxDate(
                fnr = "123",
                sykepengerMaxDate = null,
                source = SykepengerMaxDateSource.INFOTRYGD
            )

            val newStoredDate = embeddedDatabase.fetchSykepengerMaxDateByFnr("123")
            assertEquals(null, newStoredDate)
        }

        it("Should store spleis utbetaling") {
            val utbetalingUtbetalt = UtbetalingUtbetalt(
                fødselsnummer = "123",
                organisasjonsnummer = "234",
                event = "ubetaling_utbetalt",
                type = "UTBETALING",
                foreløpigBeregnetSluttPåSykepenger = LocalDate.now().plusDays(100),
                forbrukteSykedager = 100,
                gjenståendeSykedager = 122,
                stønadsdager = 10,
                antallVedtak = 4,
                fom = LocalDate.now().minusDays(50),
                tom = LocalDate.now().minusDays(10),
                utbetalingId = UUID.randomUUID().toString(),
                korrelasjonsId = UUID.randomUUID().toString(),
            )

            sykepengerMaxDateService.processUtbetalingSpleisEvent(utbetalingUtbetalt)

            val storedGjenstaaendeDager = embeddedDatabase.fetchSpleisUtbetalingByFnr("123")

            assertEquals(122, storedGjenstaaendeDager.first())
        }

        it("Should ignore duplicate spleis utbetaling") {
            val utbetalingUtbetalt = UtbetalingUtbetalt(
                fødselsnummer = "123",
                organisasjonsnummer = "234",
                event = "ubetaling_utbetalt",
                type = "UTBETALING",
                foreløpigBeregnetSluttPåSykepenger = LocalDate.now().plusDays(100),
                forbrukteSykedager = 100,
                gjenståendeSykedager = 122,
                stønadsdager = 10,
                antallVedtak = 4,
                fom = LocalDate.now().minusDays(50),
                tom = LocalDate.now().minusDays(10),
                utbetalingId = UUID.randomUUID().toString(),
                korrelasjonsId = UUID.randomUUID().toString(),
            )

            sykepengerMaxDateService.processUtbetalingSpleisEvent(utbetalingUtbetalt)
            sykepengerMaxDateService.processUtbetalingSpleisEvent(utbetalingUtbetalt)

            val storedGjenstaaendeDager = embeddedDatabase.fetchSpleisUtbetalingByFnr("123")

            assertEquals(1, storedGjenstaaendeDager.size)
            assertEquals(122, storedGjenstaaendeDager.first())
        }
    }
})
