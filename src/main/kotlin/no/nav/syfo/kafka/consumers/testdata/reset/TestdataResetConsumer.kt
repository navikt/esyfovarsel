package no.nav.syfo.kafka.consumers.testdata.reset

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.common.*
import no.nav.syfo.service.TestdataResetService
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class TestdataResetConsumer(
    val env: Environment,
    private val testdataResetService: TestdataResetService
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger(TestdataResetConsumer::class.java)
    private val kafkaListener: KafkaConsumer<String, String>

    init {
        val kafkaConfig = testdataResetProperties(env)
        kafkaListener = KafkaConsumer(kafkaConfig)
        kafkaListener.subscribe(listOf(topicTestdataReset))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic $topicTestdataReset")
        while (applicationState.running) {
            kafkaListener.poll(pollDurationInMillis).forEach {
                log.info("Received record from topic $topicTestdataReset")
                try {
                    if (it.value() != null) {
                        testdataResetService.resetTestdata(PersonIdent(it.value()))
                    } else {
                        log.warn(
                            "TestdataResetConsumer: Value of ConsumerRecord from topic $topicTestdataReset is null"
                        )
                    }
                } catch (e: Exception) {
                    log.error("TestdataResetConsumer: Exception in [$topicTestdataReset]-listener: ${e.message}", e)
                }
                kafkaListener.commitSync()
            }
        }
    }

    fun testdataResetProperties(env: Environment): Properties {
        val commonConsumerProperties = consumerProperties(env)
        return commonConsumerProperties.apply {
            remove(CommonClientConfigs.GROUP_ID_CONFIG)
            put(CommonClientConfigs.GROUP_ID_CONFIG, "esyfovarsel-group-testdata-reset-01")
        }
    }
}
