package no.nav.syfo.db

import no.nav.syfo.db.domain.PUtbetaling
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldBeEmpty
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

        it("Should include utbetaling with max date 14 days in the future, and gjenstående sykedager < 80") {
            val spleisUtbetaling = spleisUtbetaling(forelopigBeregnetSluttPaSykepenger = now().plusDays(14), gjenstaendeSykedager = 79)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling)

            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.shouldHaveSingleItem()
            merVeiledningVarslerToSend.skalInneholde(spleisUtbetaling)
        }

        it("Should not include utbetaling with max date before 14 days in the future") {
            val spleisUtbetaling1 = spleisUtbetaling(forelopigBeregnetSluttPaSykepenger = now().plusDays(13))
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)

            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.shouldBeEmpty()
        }

        it("Should not include utbetaling with gjenstaende sykepenger >= 80") {
            val spleisUtbetaling1 = spleisUtbetaling(gjenstaendeSykedager = 80)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)

            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.shouldBeEmpty()
        }

        it("Should fetch utbetaling with latest utbetalt tom") {
            val spleisUtbetaling = spleisUtbetaling()
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling)
            embeddedDatabase.storeInfotrygdUtbetaling(arbeidstakerFnr1, now().plusMonths(3), now().minusMonths(1), 60)

            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.shouldHaveSingleItem()
            merVeiledningVarslerToSend.skalInneholde(spleisUtbetaling)
        }

        it("Should fetch newest utbetaling with same utbetalt tom") {
            val spleisUtbetaling1 = spleisUtbetaling(forelopigBeregnetSluttPaSykepenger = now().plusDays(15), gjenstaendeSykedager = 59)
            val spleisUtbetaling2 = spleisUtbetaling(forelopigBeregnetSluttPaSykepenger = now().plusDays(16), gjenstaendeSykedager = 60)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling2)

            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.shouldHaveSingleItem()
            merVeiledningVarslerToSend.skalInneholde(spleisUtbetaling2)
        }

        it("Should fetch correct fnr") {
            val spleisUtbetaling1 = spleisUtbetaling(fnr = arbeidstakerFnr1, gjenstaendeSykedager = 80)
            val spleisUtbetaling2 = spleisUtbetaling(fnr = arbeidstakerFnr2, gjenstaendeSykedager = 79)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling2)

            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.shouldHaveSingleItem()
            merVeiledningVarslerToSend.skalInneholde(spleisUtbetaling2)
        }
    }
})

private fun spleisUtbetaling(
    fnr: String = arbeidstakerFnr1,
    tom: LocalDate = now().minusDays(1),
    forelopigBeregnetSluttPaSykepenger: LocalDate = now().plusDays(14),
    gjenstaendeSykedager: Int = 79
) = UtbetalingUtbetalt(
    fødselsnummer = fnr,
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

private fun List<PUtbetaling>.skalInneholde(spleisUtbetaling: UtbetalingUtbetalt) =
    this.shouldMatchAtLeastOneOf { pUtbetaling: PUtbetaling -> pUtbetaling.fnr == spleisUtbetaling.fødselsnummer && pUtbetaling.utbetaltTom == spleisUtbetaling.tom && pUtbetaling.forelopigBeregnetSlutt == spleisUtbetaling.foreløpigBeregnetSluttPåSykepenger && pUtbetaling.gjenstaendeSykedager == spleisUtbetaling.gjenståendeSykedager }
