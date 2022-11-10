package no.nav.syfo.db

import no.nav.syfo.db.domain.PUtbetaling
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldMatchAtLeastOneOf
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDate.now


object UtbetalingerDAOSpek : Spek({

    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    describe("SykepengerMaxDateDAOSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        xit("Should fetch utbetalinger that should result in mer veiledning varsel") {
            val spleisUtbetaling1 = spleisUtbetaling(tom = now().minusDays(3), forelopigBeregnetSluttPaSykepenger = now().plusDays(15), gjenstaendeSykedager = 79)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)
            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.skalInneholde(
                spleisUtbetaling1.tom,
                spleisUtbetaling1.foreløpigBeregnetSluttPåSykepenger!!,
                spleisUtbetaling1.gjenståendeSykedager!!
            )
        }

    }
})

private fun spleisUtbetaling(tom: LocalDate, forelopigBeregnetSluttPaSykepenger: LocalDate, gjenstaendeSykedager: Int) = UtbetalingUtbetalt(
    arbeidstakerFnr1,
    "123",
    "utbetalt",
    null,
    forelopigBeregnetSluttPaSykepenger,
    100,
    gjenstaendeSykedager,
    100,
    1,
    forelopigBeregnetSluttPaSykepenger.minusDays(100),
    tom,
    "123",
    "123"
)


private fun List<PUtbetaling>.skalInneholde(utbetaltTom: LocalDate, forelopigBeregnetSlutt: LocalDate, gjenstaendeSykedager: Int) =
    this.shouldMatchAtLeastOneOf { pUtbetaling: PUtbetaling -> pUtbetaling.utbetaltTom == utbetaltTom && pUtbetaling.forelopigBeregnetSlutt == forelopigBeregnetSlutt && pUtbetaling.gjenstaendeSykedager == gjenstaendeSykedager }
