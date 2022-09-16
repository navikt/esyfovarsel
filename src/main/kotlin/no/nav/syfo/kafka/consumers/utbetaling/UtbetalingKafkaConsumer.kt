package no.nav.syfo.kafka.consumers.utbetaling

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.*
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

class UtbetalingKafkaConsumer(
    env: Environment,
//    val somethingService: SomethingService
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.UtbetalingKafkaConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val objectMapper = createObjectMapper()

    init {
        val kafkaConfig = aivenConsumerProperties(env)
        kafkaListener = KafkaConsumer(kafkaConfig)
        kafkaListener.subscribe(listOf(topicUtbetaling))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic $topicUtbetaling")
        while (applicationState.running) {
            kafkaListener.poll(zeroMillis).forEach {
                log.info("Received message ${it.key()} from topic $topicUtbetaling")
                try {
                    val utbetaling: UtbetalingUtbetalt = objectMapper.readValue(it.value())
                    log.info("Utbetaling content: {}", utbetaling)
//                    somethingService.processUtbetalingMelding(utbetaling)
                } catch (e: IOException) {
                    log.error(
                        "Error in [$topicUtbetaling]-listener: Could not parse message | ${e.message}",
                        e
                    )
                } catch (e: Exception) {
                    log.error(
                        "Exception in [$topicUtbetaling]-listener: ${e.message}",
                        e
                    )
                }
                kafkaListener.commitSync()
            }
        }
    }
}
