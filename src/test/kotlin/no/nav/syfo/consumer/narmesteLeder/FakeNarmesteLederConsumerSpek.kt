package no.nav.syfo.consumer.narmesteLeder

import io.kotest.core.spec.style.DescribeSpec
import org.amshove.kluent.shouldBeEqualTo

class FakeNarmesteLederConsumerSpek :
    DescribeSpec({
        describe("FakeNarmesteLederConsumer") {
            it("returns synthetic fnr values instead of reflecting input fnr") {
                val ansattFnr = "12345678910"
                val relasjon =
                    FakeNarmesteLederConsumer().getNarmesteLeder(
                        ansattFnr = ansattFnr,
                        orgnummer = "999999999",
                    )
                relasjon.narmesteLederFnr shouldBeEqualTo ansattFnr.reversed()
                relasjon.narmesteLederId shouldBeEqualTo "local-narmeste-leder"
                relasjon.narmesteLederEpost shouldBeEqualTo "narmeste.leder@example.invalid"
            }
        }
    })
