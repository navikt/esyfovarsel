package no.nav.syfo.kafka.consumers.varselbus

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.getTestEnv
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataAltinnRessurs
import no.nav.syfo.kafka.consumers.varselbus.domain.getArbeidsgiverAltinnRessurs
import no.nav.syfo.service.VarselBusService
import org.apache.kafka.clients.consumer.ConsumerRecord
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

class VarselBusKafkaConsumerSpek :
    DescribeSpec({
        val varselBusService = mockk<VarselBusService>(relaxed = true)
        val consumer = VarselBusKafkaConsumer(env = getTestEnv(), varselBusService = varselBusService)

        beforeTest {
            clearAllMocks()
        }

        describe("VarselBusKafkaConsumer") {
            describe("arbeidsgiverhendelser") {
                it("går ikke inn i mikrofrontend-sporet") {
                    val capturedHendelse = slot<EsyfovarselHendelse>()
                    coEvery { varselBusService.processVarselHendelse(capture(capturedHendelse)) } returns Unit

                    val record =
                        ConsumerRecord(
                            "team-esyfo.varselbus",
                            0,
                            0L,
                            "123",
                            arbeidsgiverHendelseJson,
                        )

                    runBlocking {
                        consumer.invokeProcessVarselBusRecord(record)
                    }

                    coVerify(exactly = 1) { varselBusService.processVarselHendelse(any()) }
                    verify(exactly = 0) { varselBusService.processVarselHendelseAsMinSideMicrofrontendEvent(any()) }

                    val hendelse = capturedHendelse.captured
                    hendelse.shouldBeTypeOf<ArbeidsgiverHendelse>()
                    hendelse.getArbeidsgiverAltinnRessurs() shouldBe
                        VarselDataAltinnRessurs(
                            id = "urn:altinn:resource:dialogmote",
                            url = "https://www.nav.no/arbeidsgiver/dialogmote",
                        )
                }
            }
        }
    })

private suspend fun VarselBusKafkaConsumer.invokeProcessVarselBusRecord(record: ConsumerRecord<String, String>) {
    val method =
        VarselBusKafkaConsumer::class.java.getDeclaredMethod(
            "processVarselBusRecord",
            ConsumerRecord::class.java,
            Continuation::class.java,
        )
    method.isAccessible = true

    suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
        val result = method.invoke(this, record, continuation)
        if (result === COROUTINE_SUSPENDED) {
            COROUTINE_SUSPENDED
        } else {
            Unit
        }
    }
}

private val arbeidsgiverHendelseJson =
    """
    {
      "@type": "ArbeidsgiverHendelse",
      "type": "AG_DIALOGMOTE_REFERAT",
      "ferdigstill": false,
      "arbeidstakerFnr": "12345678910",
      "data": {
        "altinnRessurs": {
          "id": "urn:altinn:resource:dialogmote",
          "url": "https://www.nav.no/arbeidsgiver/dialogmote"
        }
      },
      "orgnummer": "999999999"
    }
    """.trimIndent()
