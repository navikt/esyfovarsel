package no.nav.syfo.kafka.consumers.utbetaling

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.*
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import no.nav.syfo.service.SykepengerMaxDateService
import org.apache.kafka.clients.CommonClientConfigs.GROUP_ID_CONFIG
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

class UtbetalingKafkaConsumer(
    val env: Environment,
    private val sykepengerMaxDateService: SykepengerMaxDateService
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.UtbetalingKafkaConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val objectMapper = createObjectMapper()

    init {
        val kafkaConfig = utbetalingSpleisConsumerProperties(env)
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
                    if (utbetaling.event == "utbetaling_utbetalt") {
                        sykepengerMaxDateService.processUtbetalingSpleisEvent(utbetaling)
                    }
                } catch (e: IOException) {
                    log.error(
                        "Error in [$topicUtbetaling]-listener: Could not parse message. Check topic Schema"
                    )
                } catch (e: Exception) {
                    log.error(
                        "Exception in [$topicUtbetaling]-listener: $e",
                        e
                    )
                }
                kafkaListener.commitSync()
            }
        }
    }

    fun utbetalingSpleisConsumerProperties(env: Environment): Properties {
        val commonConsumerProperties = aivenConsumerProperties(env)
        return commonConsumerProperties.apply {
            remove(GROUP_ID_CONFIG)
            put(GROUP_ID_CONFIG, "esyfovarsel-group-utbetaling-spleis-01")
        }
    }
}
