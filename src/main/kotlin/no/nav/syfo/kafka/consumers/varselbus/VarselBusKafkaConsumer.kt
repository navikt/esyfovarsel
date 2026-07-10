package no.nav.syfo.kafka.consumers.varselbus

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CancellationException
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.KafkaListener
import no.nav.syfo.kafka.common.TOPIC_VARSEL_BUS
import no.nav.syfo.kafka.common.consumerProperties
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.common.kafkaConsumerCloseDuration
import no.nav.syfo.kafka.common.pollDurationInMillis
import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.isArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toArbeidstakerHendelse
import no.nav.syfo.service.VarselBusService
import org.apache.kafka.clients.consumer.CloseOptions
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselBusKafkaConsumer(
    val env: Environment,
    val varselBusService: VarselBusService,
    private val kafkaListener: KafkaConsumer<String, String> = KafkaConsumer(consumerProperties(env)),
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger(VarselBusKafkaConsumer::class.qualifiedName)
    private val objectMapper = createObjectMapper()

    init {
        kafkaListener.subscribe(listOf(TOPIC_VARSEL_BUS))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic $TOPIC_VARSEL_BUS")

        try {
            while (applicationState.running) {
                try {
                    kafkaListener.poll(pollDurationInMillis).forEach { processVarselBusRecord(it) }
                    kafkaListener.commitSync()
                } catch (e: WakeupException) {
                    if (applicationState.running) {
                        throw e
                    }
                    log.info("Kafka consumer for topic $TOPIC_VARSEL_BUS received wakeup signal, shutting down")
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error(
                        "Exception in [$TOPIC_VARSEL_BUS]-listener: ${e.message}",
                        e,
                    )
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

    private suspend fun processVarselBusRecord(record: ConsumerRecord<String, String>) {
        val varselEvent: EsyfovarselHendelse = objectMapper.readValue(record.value())
        varselEvent.data = objectMapper.readTree(record.value())["data"]
        log.info("VARSEL BUS: Mottatt melding med UUID ${record.key()} av type: ${varselEvent.type}")

        varselBusService.processVarselHendelse(varselEvent)

        if (varselEvent.isArbeidstakerHendelse()) {
            varselBusService.processVarselHendelseAsMinSideMicrofrontendEvent(varselEvent.toArbeidstakerHendelse())
        }
    }

    private fun closeKafkaListener() {
        try {
            kafkaListener.close(CloseOptions.timeout(kafkaConsumerCloseDuration))
        } catch (e: Exception) {
            log.warn("Error closing kafka consumer for topic $TOPIC_VARSEL_BUS", e)
        }
    }
}
