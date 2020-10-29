package no.nav.syfo.consumer.syketilfelle

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.fullPath
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.token.TokenConsumer
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SyketilfelleConsumerSpek : Spek({
    val mockClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when (request.url.fullPath) {
                    "/oppfolgingstilfelle/beregn/syfomoteadmin/aktoerId" -> {
                        respond("""{"antallBrukteDager": 0, "oppbruktArbeidsgvierperiode": false, "arbeidsgiverperiode": {"fom": "2020-01-01", "tom": "2020-02-01"}}""")
                    }
                    else -> error("Endepunktet finnes ikke ${request.url.fullPath}")
                }
            }
        }
    }

    describe("SyketilfelleConsumerSpek") {
        it("beregnOppfolgingstilfelle") {
            val tokenConsumer: TokenConsumer = mockk()
            val syketilfelleConsumer = SyketilfelleConsumer(tokenConsumer, mockClient)

            coEvery { tokenConsumer.token() } returns "TOKEN"

            runBlocking {
                val oppfolgingstilfelle = syketilfelleConsumer.beregnOppfolgingstilfelle("aktoerId")
                oppfolgingstilfelle.antallBrukteDager shouldEqual 0
                oppfolgingstilfelle.oppbruktArbeidsgvierperiode shouldEqual false
                oppfolgingstilfelle.arbeidsgiverperiode shouldEqual Periode(LocalDate.of(2020, 1, 1),LocalDate.of(2020, 2, 1))
            }
        }
    }
})
