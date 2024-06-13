package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.consumer.pdl.Foedselsdato
import no.nav.syfo.consumer.pdl.HentPerson
import no.nav.syfo.consumer.pdl.HentPersonData
import no.nav.syfo.consumer.pdl.Navn
import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.kafka.consumers.utbetaling.domain.UTBETALING_UTBETALT
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingSpleis
import no.nav.syfo.testutil.EmbeddedDatabase
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

class SykepengerMaxDateServiceSpek : DescribeSpec({
    describe("SykepengerMaxDateService") {
        val embeddedDatabase = EmbeddedDatabase()
        val pdlConsumer = mockk<PdlConsumer>(relaxed = true)
        val sykepengerMaxDateService = SykepengerMaxDateService(embeddedDatabase, pdlConsumer)
        coEvery { pdlConsumer.hentPerson(any()) } returns HentPersonData(
            hentPerson = HentPerson(
                foedselsdato = listOf(Foedselsdato(foedselsdato = "1990-01-01")),
                navn = listOf(
                    Navn(
                        fornavn = "Test",
                        mellomnavn = null,
                        etternavn = "Testesen"
                    )
                )
            )
        )
        beforeTest {
            embeddedDatabase.dropData()
        }

        it("Should store spleis utbetaling") {
            val utbetalingUtbetalt = UtbetalingSpleis(
                fødselsnummer = "123",
                organisasjonsnummer = "234",
                event = UTBETALING_UTBETALT,
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
            val utbetalingUtbetalt = UtbetalingSpleis(
                fødselsnummer = "123",
                organisasjonsnummer = "234",
                event = UTBETALING_UTBETALT,
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
