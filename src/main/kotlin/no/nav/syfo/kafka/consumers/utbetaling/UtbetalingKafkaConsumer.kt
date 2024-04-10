package no.nav.syfo.kafka.consumers.utbetaling

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.*
import no.nav.syfo.kafka.consumers.utbetaling.domain.UTBETALING_UTBETALT
import no.nav.syfo.kafka.consumers.utbetaling.domain.UTBETALING_UTEN_UTBETALING
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingSpleis
import no.nav.syfo.service.SykepengerMaxDateService
import org.apache.kafka.clients.CommonClientConfigs.GROUP_ID_CONFIG
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

class UtbetalingKafkaConsumer(
    val env: Environment,
    private val sykepengerMaxDateService: SykepengerMaxDateService,
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger(UtbetalingKafkaConsumer::class.java)
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
            kafkaListener.poll(pollDurationInMillis).forEach {
                log.info("Received record from topic $topicUtbetaling")
                processRecord(it)
                kafkaListener.commitSync()
            }
        }
    }

    private suspend fun processRecord(record: ConsumerRecord<String, String>) {
        try {
            val utbetaling: UtbetalingSpleis = objectMapper.readValue(record.value())
            if (utbetaling.event == UTBETALING_UTBETALT || utbetaling.event == UTBETALING_UTEN_UTBETALING) {
                sykepengerMaxDateService.processUtbetalingSpleisEvent(utbetaling)
            }
        } catch (e: IOException) {
            log.error("Error in [$topicUtbetaling]-listener: Could not parse message. Check topic Schema")
        } catch (e: Exception) {
            log.error("Exception in [$topicUtbetaling]-listener: $e", e)
        }
    }

    private fun utbetalingSpleisConsumerProperties(env: Environment): Properties {
        val commonConsumerProperties = consumerProperties(env)
        return commonConsumerProperties.apply {
            remove(GROUP_ID_CONFIG)
            put(GROUP_ID_CONFIG, "esyfovarsel-group-utbetaling-spleis-01")
        }
    }
}
