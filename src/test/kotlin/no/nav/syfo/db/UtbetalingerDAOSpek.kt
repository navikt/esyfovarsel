package no.nav.syfo.db

import io.kotest.core.spec.style.DescribeSpec
import no.nav.syfo.db.domain.PUtbetaling
import no.nav.syfo.kafka.consumers.infotrygd.domain.InfotrygdSource.AAP_KAFKA_TOPIC
import no.nav.syfo.kafka.consumers.utbetaling.domain.UTBETALING_UTBETALT
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingSpleis
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.should
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldHaveSingleItem
import org.amshove.kluent.shouldMatchAtLeastOneOf
import java.time.LocalDate
import java.time.LocalDate.now
import java.util.*

class UtbetalingerDAOSpek : DescribeSpec({
    describe("UtbetalingerDAOSpek") {
        val embeddedDatabase = EmbeddedDatabase()
        val nowPlus1Day = now().plusDays(1)
        val nowPlus2Days = now().plusDays(2)
        val nowMinus1Day = now().minusDays(1)
        val nowMinus2Days = now().minusDays(2)

        beforeTest {
            embeddedDatabase.connection.dropData()
        }

        it("Should include utbetaling with max date 14 days in the future, and gjenstående sykedager < 91") {
            val spleisUtbetaling =
                spleisUtbetaling(forelopigBeregnetSluttPaSykepenger = now().plusDays(14), gjenstaendeSykedager = 90)
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

        it("Should not include utbetaling with gjenstaende sykepenger >= 91") {
            val spleisUtbetaling1 = spleisUtbetaling(gjenstaendeSykedager = 91)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)

            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.shouldBeEmpty()
        }

        it("Should fetch utbetaling with latest utbetalt tom") {
            val spleisUtbetaling = spleisUtbetaling()
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling)
            embeddedDatabase.storeInfotrygdUtbetaling(
                arbeidstakerFnr1,
                now().plusMonths(3),
                now().minusMonths(1),
                60,
                AAP_KAFKA_TOPIC
            )

            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.shouldHaveSingleItem()
            merVeiledningVarslerToSend.skalInneholde(spleisUtbetaling)
        }

        it("Should fetch newest utbetaling with same utbetalt tom") {
            val spleisUtbetaling1 =
                spleisUtbetaling(forelopigBeregnetSluttPaSykepenger = now().plusDays(15), gjenstaendeSykedager = 59)
            val spleisUtbetaling2 =
                spleisUtbetaling(forelopigBeregnetSluttPaSykepenger = now().plusDays(16), gjenstaendeSykedager = 60)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling2)

            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.shouldHaveSingleItem()
            merVeiledningVarslerToSend.skalInneholde(spleisUtbetaling2)
        }

        it("Should fetch correct fnr") {
            val spleisUtbetaling1 = spleisUtbetaling(fnr = arbeidstakerFnr1, gjenstaendeSykedager = 91)
            val spleisUtbetaling2 = spleisUtbetaling(fnr = arbeidstakerFnr2, gjenstaendeSykedager = 90)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling2)

            val merVeiledningVarslerToSend = embeddedDatabase.fetchMerVeiledningVarslerToSend()
            merVeiledningVarslerToSend.shouldHaveSingleItem()
            merVeiledningVarslerToSend.skalInneholde(spleisUtbetaling2)
        }

        it("Should fetch maxdate from latest utbetaling") {
            val spleisUtbetaling1 = spleisUtbetaling(
                fnr = arbeidstakerFnr1,
                tom = nowMinus1Day,
                forelopigBeregnetSluttPaSykepenger = nowPlus1Day
            )
            val spleisUtbetaling2 = spleisUtbetaling(
                fnr = arbeidstakerFnr1,
                tom = nowMinus2Days,
                forelopigBeregnetSluttPaSykepenger = nowPlus2Days
            )
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling2)

            embeddedDatabase.shouldContainForelopigBeregnetSlutt(arbeidstakerFnr1, nowPlus1Day)
        }

        it("Should fetch maxdate from spleis when latest utbetaling from spleis") {
            val spleisUtbetaling1 = spleisUtbetaling(
                fnr = arbeidstakerFnr1,
                tom = nowMinus1Day,
                forelopigBeregnetSluttPaSykepenger = nowPlus1Day
            )
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)
            embeddedDatabase.storeInfotrygdUtbetaling(
                arbeidstakerFnr1,
                nowPlus2Days,
                nowMinus2Days,
                60,
                AAP_KAFKA_TOPIC
            )

            embeddedDatabase.shouldContainForelopigBeregnetSlutt(arbeidstakerFnr1, nowPlus1Day)
        }

        it("Should fetch maxdate from infotrygd when latest utbetaling from infotrygd") {
            val spleisUtbetaling1 = spleisUtbetaling(
                fnr = arbeidstakerFnr1,
                tom = nowMinus2Days,
                forelopigBeregnetSluttPaSykepenger = nowPlus2Days
            )
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)
            embeddedDatabase.storeInfotrygdUtbetaling(arbeidstakerFnr1, nowPlus1Day, nowMinus1Day, 60, AAP_KAFKA_TOPIC)

            embeddedDatabase.shouldContainForelopigBeregnetSlutt(arbeidstakerFnr1, nowPlus1Day)
        }

        it("Should fetch maxdate for correct fnr") {
            val spleisUtbetaling1 = spleisUtbetaling(
                fnr = arbeidstakerFnr1,
                tom = nowMinus1Day,
                forelopigBeregnetSluttPaSykepenger = nowPlus1Day
            )
            val spleisUtbetaling2 = spleisUtbetaling(
                fnr = arbeidstakerFnr2,
                tom = nowMinus2Days,
                forelopigBeregnetSluttPaSykepenger = nowPlus2Days
            )
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling1)
            embeddedDatabase.storeSpleisUtbetaling(spleisUtbetaling2)

            embeddedDatabase.shouldContainForelopigBeregnetSlutt(arbeidstakerFnr2, nowPlus2Days)
        }
    }
})

private fun spleisUtbetaling(
    fnr: String = arbeidstakerFnr1,
    tom: LocalDate = now().minusDays(1),
    forelopigBeregnetSluttPaSykepenger: LocalDate = now().plusDays(14),
    gjenstaendeSykedager: Int = 90
) = UtbetalingSpleis(
    fødselsnummer = fnr,
    organisasjonsnummer = "234",
    event = UTBETALING_UTBETALT,
    type = "UTBETALING",
    foreløpigBeregnetSluttPåSykepenger = forelopigBeregnetSluttPaSykepenger,
    forbrukteSykedager = 89,
    gjenståendeSykedager = gjenstaendeSykedager,
    stønadsdager = 10,
    antallVedtak = 4,
    fom = now().minusDays(50),
    tom = tom,
    utbetalingId = UUID.randomUUID().toString(),
    korrelasjonsId = UUID.randomUUID().toString(),
)

private fun List<PUtbetaling>.skalInneholde(spleisUtbetaling: UtbetalingSpleis) =
    this.shouldMatchAtLeastOneOf { pUtbetaling: PUtbetaling -> pUtbetaling.fnr == spleisUtbetaling.fødselsnummer && pUtbetaling.utbetaltTom == spleisUtbetaling.tom && pUtbetaling.forelopigBeregnetSlutt == spleisUtbetaling.foreløpigBeregnetSluttPåSykepenger && pUtbetaling.gjenstaendeSykedager == spleisUtbetaling.gjenståendeSykedager }

private fun DatabaseInterface.shouldContainForelopigBeregnetSlutt(fnr: String, forelopigBeregnetSlutt: LocalDate) =
    this.should("Should contain row with requested fnr and forelopigBeregnetSlutt") {
        this.fetchMaksDatoByFnr(fnr)?.forelopig_beregnet_slutt == forelopigBeregnetSlutt
    }
