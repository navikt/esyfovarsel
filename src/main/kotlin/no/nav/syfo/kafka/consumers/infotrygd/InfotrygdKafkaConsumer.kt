package no.nav.syfo.kafka.consumers.infotrygd

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.*
import no.nav.syfo.kafka.consumers.infotrygd.domain.InfotrygdSource
import no.nav.syfo.kafka.consumers.infotrygd.domain.KInfotrygdSykepengedager
import no.nav.syfo.service.SykepengerMaxDateService
import no.nav.syfo.utils.parseDate
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

class InfotrygdKafkaConsumer(
    val env: Environment,
    private val sykepengerMaxDateService: SykepengerMaxDateService
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.consumers.infotrygd.InfotrygdKafkaConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val objectMapper = createObjectMapper()

    init {
        val kafkaConfig = infotrygdConsumerProperties(env)
        kafkaListener = KafkaConsumer(kafkaConfig)
        kafkaListener.subscribe(listOf(topicSykepengedagerInfotrygd))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic $topicSykepengedagerInfotrygd")
        while (applicationState.running) {
            kafkaListener.poll(pollDurationInMillis).forEach {
                log.info("Received record from topic $topicSykepengedagerInfotrygd. Opprettet ${it.timestamp()}")
                try {
                    val kInfotrygdSykepengedager: KInfotrygdSykepengedager = objectMapper.readValue(it.value())

                    val fnr = kInfotrygdSykepengedager.after.F_NR
                    val sykepengerMaxDate = parseDate(kInfotrygdSykepengedager.after.MAX_DATO)
                    val utbetaltTom = kInfotrygdSykepengedager.after.UTBET_TOM
                    if (utbetaltTom != null) {
                        val utbetaltTomDate = parseDate(utbetaltTom)
                        sykepengerMaxDateService.processInfotrygdEvent(
                            fnr,
                            sykepengerMaxDate,
                            utbetaltTomDate,
                            utbetaltTomDate.gjenstaendeSykepengedager(sykepengerMaxDate),
                            InfotrygdSource.AAP_KAFKA_TOPIC
                        )
                    }
                } catch (e: IOException) {
                    log.error(
                        "Error in [$topicSykepengedagerInfotrygd]-listener: Could not parse message | ${e.message}",
                        e
                    )
                } catch (e: Exception) {
                    log.error(
                        "Exception in [$topicSykepengedagerInfotrygd]-listener: ${e.message}",
                        e
                    )
                }
                kafkaListener.commitSync()
            }
        }
    }

    fun infotrygdConsumerProperties(env: Environment): Properties {
        val commonConsumerProperties = aivenConsumerProperties(env)
        return commonConsumerProperties.apply {
            remove(CommonClientConfigs.GROUP_ID_CONFIG)
            put(CommonClientConfigs.GROUP_ID_CONFIG, "esyfovarsel-group-infotrygd-01")
        }
    }
}
