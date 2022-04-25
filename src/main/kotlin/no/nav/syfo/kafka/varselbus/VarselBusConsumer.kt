package no.nav.syfo.kafka.varselbus

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.KafkaListener
import no.nav.syfo.kafka.aivenConsumerProperties
import no.nav.syfo.kafka.topicVarselBus
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.service.VarselBusService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration

class VarselBusKafkaConsumer(
    env: Environment,
    val varselBusService: VarselBusService
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.VarselBusConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val zeroMillis = Duration.ofMillis(0L)
    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
    }

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
                    val varsel: EsyfovarselHendelse = objectMapper.readValue(it.value())
                    log.info("VARSEL BUS: Innhold | fnr: ${varsel.mottakerFnr} | kontekst: ${varsel.type}")
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
