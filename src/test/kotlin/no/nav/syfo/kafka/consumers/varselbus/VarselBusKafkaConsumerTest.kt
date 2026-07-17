package no.nav.syfo.kafka.consumers.varselbus

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.kafkaConsumerCloseDuration
import no.nav.syfo.service.VarselBusService
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.CloseOptions
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException

class VarselBusKafkaConsumerTest :
    DescribeSpec({
        describe("VarselBusKafkaConsumer") {
            it("should stop cleanly and close kafka consumer when wakeup is triggered during shutdown") {
                val kafkaListener = mockk<KafkaConsumer<String, String>>(relaxed = true)
                val applicationState = ApplicationState(running = true)
                val closeOptionsSlot = slot<CloseOptions>()
                every {
                    kafkaListener.poll(any())
                } answers {
                    applicationState.running = false
                    throw WakeupException()
                }
                val consumer =
                    VarselBusKafkaConsumer(
                        env = mockk<Environment>(relaxed = true),
                        varselBusService = mockk<VarselBusService>(relaxed = true),
                        kafkaListener = kafkaListener,
                    )

                runBlocking {
                    consumer.listen(applicationState)
                }

                verify(exactly = 1) { kafkaListener.close(capture(closeOptionsSlot)) }
                closeOptionsSlot.captured.timeout().orElse(null) shouldBeEqualTo kafkaConsumerCloseDuration
                verify(exactly = 0) { kafkaListener.commitSync() }
            }

            it("should delegate wakeup to the underlying kafka consumer") {
                val kafkaListener = mockk<KafkaConsumer<String, String>>(relaxed = true)
                val consumer =
                    VarselBusKafkaConsumer(
                        env = mockk<Environment>(relaxed = true),
                        varselBusService = mockk<VarselBusService>(relaxed = true),
                        kafkaListener = kafkaListener,
                    )

                consumer.wakeup()

                verify(exactly = 1) { kafkaListener.wakeup() }
            }
        }
    })
