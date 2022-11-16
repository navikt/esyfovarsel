package no.nav.syfo.db

import java.time.LocalDate
import java.util.*
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals

object UtbetalingSpleisDAOSpek : Spek({

    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    describe("UtbetalingSpleisDAOSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
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
            embeddedDatabase.storeSpleisUtbetaling(utbetalingUtbetalt)
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

            embeddedDatabase.storeSpleisUtbetaling(utbetalingUtbetalt)
            embeddedDatabase.storeSpleisUtbetaling(utbetalingUtbetalt)

            val storedGjenstaaendeDager = embeddedDatabase.fetchSpleisUtbetalingByFnr("123")

            assertEquals(1, storedGjenstaaendeDager.size)
            assertEquals(122, storedGjenstaaendeDager.first())
        }
    }
})
