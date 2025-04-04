package no.nav.syfo.arbeidstakervarsel.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.arbeidstakervarsel.service.ArbeidstakervarselService
import no.nav.syfo.kafka.common.KafkaListener
import no.nav.syfo.kafka.common.consumerProperties
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.common.pollDurationInMillis
import no.nav.syfo.kafka.common.topicArbeidstakerVarsel
import no.nav.syfo.kafka.common.topicVarselBus
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerVarsel
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ArbeidstakervarselKafkaConsumer(
    val env: Environment,
    val arbeidstakervarselService: ArbeidstakervarselService
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger(ArbeidstakervarselKafkaConsumer::class.qualifiedName)
    private val kafkaListener: KafkaConsumer<String, String>
    private val objectMapper = createObjectMapper()

    init {
        val kafkaConfig = consumerProperties(env)
        kafkaListener = KafkaConsumer(kafkaConfig)
        kafkaListener.subscribe(listOf(topicArbeidstakerVarsel))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic $topicArbeidstakerVarsel")

        while (applicationState.running) {
            try {
                kafkaListener.poll(pollDurationInMillis).forEach { processArbeidstakerVarselRecord(it) }
                kafkaListener.commitSync()
            } catch (e: Exception) {
                log.error(
                    "Exception in [$topicVarselBus]-listener: ${e.message}",
                    e
                )
                kafkaListener.commitSync()
            }
        }
    }

    private suspend fun processArbeidstakerVarselRecord(record: ConsumerRecord<String, String>) {
        val arbeidstakerVarsel: ArbeidstakerVarsel = objectMapper.readValue(record.value())
        arbeidstakervarselService.processVarsel(arbeidstakerVarsel)
    }
}
