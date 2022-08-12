package no.nav.syfo.kafka.consumers.varselbus

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.*
import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.Mottaker
import no.nav.syfo.service.VarselBusService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

class VarselBusKafkaConsumer(
    env: Environment,
    val varselBusService: VarselBusService
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.VarselBusConsumer")
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
            kafkaListener.poll(zeroMillis).forEach {
                log.info("VARSEL BUS: Mottatt melding ${it.key()} fra topic")
                try {
                    val varsel: EsyfovarselHendelse<out Mottaker> = objectMapper.readValue(it.value())
                    varsel.data = objectMapper.readTree(it.value())["data"]
                    log.info("VARSEL BUS: Melding med UUID ${it.key()} er av type: ${varsel.type}")
                    varselBusService.processVarselHendelse(varsel)
                } catch (e: IOException) {
                    log.error(
                        "Error in [$topicVarselBus]-listener: Could not parse message | ${e.message}",
                        e
                    )
                } catch (e: Exception) {
                    log.error(
                        "Exception in [$topicVarselBus]-listener: ${e.message}",
                        e
                    )
                }
                kafkaListener.commitSync()
            }
        }
    }
}
