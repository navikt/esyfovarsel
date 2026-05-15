package no.nav.syfo.consumer.narmesteLeder

import io.kotest.core.spec.style.DescribeSpec
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo

class FakeNarmesteLederConsumerSpek :
    DescribeSpec({
        describe("FakeNarmesteLederConsumer") {
            it("returns synthetic fnr values instead of reflecting input fnr") {
                val response =
                    runBlocking {
                        FakeNarmesteLederConsumer().getNarmesteLeder(
                            ansattFnr = "12345678910",
                            orgnummer = "999999999",
                        )
                    }
                val relasjon = response.narmesteLederRelasjon

                relasjon?.fnr shouldBeEqualTo FakeNarmesteLederConsumer.LOCAL_SYNTHETIC_FNR
                relasjon?.narmesteLederFnr shouldBeEqualTo FakeNarmesteLederConsumer.LOCAL_SYNTHETIC_FNR
            }
        }
    })
