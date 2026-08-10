package no.nav.syfo.kafka.consumers.testdata.reset

import kotlinx.coroutines.CancellationException
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.common.KafkaListener
import no.nav.syfo.kafka.common.TOPIC_TESTDATA_RESET
import no.nav.syfo.kafka.common.consumerProperties
import no.nav.syfo.kafka.common.kafkaConsumerCloseDuration
import no.nav.syfo.kafka.common.pollDurationInMillis
import no.nav.syfo.service.TestdataResetService
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.CloseOptions
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Properties

class TestdataResetConsumer(
    val env: Environment,
    private val testdataResetService: TestdataResetService,
    private val kafkaListener: KafkaConsumer<String, String> = KafkaConsumer(createTestdataResetProperties(env)),
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger(TestdataResetConsumer::class.java)

    init {
        kafkaListener.subscribe(listOf(TOPIC_TESTDATA_RESET))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic $TOPIC_TESTDATA_RESET")
        try {
            while (applicationState.running) {
                try {
                    kafkaListener.poll(pollDurationInMillis).forEach {
                        log.info("Received record from topic $TOPIC_TESTDATA_RESET")
                        if (it.value() != null) {
                            testdataResetService.resetTestdata(PersonIdent(it.value()))
                        } else {
                            log.warn(
                                "TestdataResetConsumer: Value of ConsumerRecord from topic $TOPIC_TESTDATA_RESET is null",
                            )
                        }
                        kafkaListener.commitSync()
                    }
                } catch (e: WakeupException) {
                    if (applicationState.running) {
                        throw e
                    }
                    log.info("Kafka consumer for topic $TOPIC_TESTDATA_RESET received wakeup signal, shutting down")
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("TestdataResetConsumer: Exception in [$TOPIC_TESTDATA_RESET]-listener: ${e.message}", e)
                    kafkaListener.commitSync()
                }
            }
        } finally {
            closeKafkaListener()
        }
    }

    override fun wakeup() {
        kafkaListener.wakeup()
    }

    private fun closeKafkaListener() {
        try {
            kafkaListener.close(CloseOptions.timeout(kafkaConsumerCloseDuration))
        } catch (e: Exception) {
            log.warn("Error closing kafka consumer for topic $TOPIC_TESTDATA_RESET", e)
        }
    }
}

private fun createTestdataResetProperties(env: Environment): Properties {
    val commonConsumerProperties = consumerProperties(env)
    return commonConsumerProperties.apply {
        remove(CommonClientConfigs.GROUP_ID_CONFIG)
        put(CommonClientConfigs.GROUP_ID_CONFIG, "esyfovarsel-group-testdata-reset-01")
    }
}
