package no.nav.syfo.kafka.consumers.varselbus

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.*
import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.service.VarselBusService
import no.nav.syfo.shutdownApplication
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselBusKafkaConsumer(
    val env: Environment,
    val varselBusService: VarselBusService
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger(VarselBusKafkaConsumer::class.qualifiedName)
    private val kafkaListener: KafkaConsumer<String, String>
    private val objectMapper = createObjectMapper()

    init {
        val kafkaConfig = aivenConsumerProperties(env)
        kafkaListener = KafkaConsumer(kafkaConfig)
        kafkaListener.subscribe(listOf(topicVarselBus))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic $topicVarselBus")

        while (applicationState.running) {
            runBlocking {
                try {
                    kafkaListener.poll(pollDurationInMillis).forEach { launch { processVarselBusRecord(it) } }
                    kafkaListener.commitSync()
                } catch (e: Exception) {
                    log.error(
                        "Exception in [$topicVarselBus]-listener: ${e.message}",
                        e
                    )
                    applicationState.shutdownApplication()
                }
            }
        }
    }

    private suspend fun processVarselBusRecord(record: ConsumerRecord<String, String>) {
        val varselEvent: EsyfovarselHendelse = objectMapper.readValue(record.value())
        varselEvent.data = objectMapper.readTree(record.value())["data"]
        log.info("VARSEL BUS: Mottatt melding med UUID ${record.key()} av type: ${varselEvent.type}")

        varselBusService.processVarselHendelse(varselEvent)

        varselBusService.processVarselHendelseAsMinSideMicrofrontendEvent(varselEvent)
    }
}
