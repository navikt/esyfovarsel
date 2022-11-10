package no.nav.syfo.db

import no.nav.syfo.db.domain.PUtbetaling
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldHaveSingleItem
import org.amshove.kluent.shouldMatchAtLeastOneOf
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDate.now
import java.util.*

object UtbetalingerDAOSpek : Spek({

    //The default timeout of 10 seconds is not sufficient to initialise the embedded database
    defaultTimeout = 20000L

    describe("UtbetalingerDAOSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Should fetch utbetalinger that should result in mer veiledning varsel") {
            val spleisUtbetaling1 = spleisUtbetaling(tom = now().minusDays(3), forelopigBeregnetSluttPaSykepenger = now().plusDays(15), gjenstaendeSykedager = 79)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)
            embeddedDatabase.storeInfotrygdUtbetaling(arbeidstakerFnr1, now().plusMonths(3), now().minusMonths(1), 60)

            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.shouldHaveSingleItem()
            merVeiledningVarslerToSend.skalInneholde(
                spleisUtbetaling1.tom,
                spleisUtbetaling1.foreløpigBeregnetSluttPåSykepenger!!,
                spleisUtbetaling1.gjenståendeSykedager!!
            )
        }

    }
})

private fun spleisUtbetaling(tom: LocalDate, forelopigBeregnetSluttPaSykepenger: LocalDate, gjenstaendeSykedager: Int) = UtbetalingUtbetalt(
    fødselsnummer = arbeidstakerFnr1,
    organisasjonsnummer = "234",
    event = "ubetaling_utbetalt",
    type = "UTBETALING",
    foreløpigBeregnetSluttPåSykepenger = forelopigBeregnetSluttPaSykepenger,
    forbrukteSykedager = 100,
    gjenståendeSykedager = gjenstaendeSykedager,
    stønadsdager = 10,
    antallVedtak = 4,
    fom = now().minusDays(50),
    tom = tom,
    utbetalingId = UUID.randomUUID().toString(),
    korrelasjonsId = UUID.randomUUID().toString(),
)


private fun List<PUtbetaling>.skalInneholde(utbetaltTom: LocalDate, forelopigBeregnetSlutt: LocalDate, gjenstaendeSykedager: Int) =
    this.shouldMatchAtLeastOneOf { pUtbetaling: PUtbetaling -> pUtbetaling.utbetaltTom == utbetaltTom && pUtbetaling.forelopigBeregnetSlutt == forelopigBeregnetSlutt && pUtbetaling.gjenstaendeSykedager == gjenstaendeSykedager }
