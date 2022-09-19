package no.nav.syfo.kafka.consumers.infotrygd

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.*
import no.nav.syfo.kafka.consumers.infotrygd.domain.KInfotrygdSykepengedager
import no.nav.syfo.service.AccessControlService
import no.nav.syfo.service.SykepengerMaxDateService
import no.nav.syfo.service.SykepengerMaxDateSource
import no.nav.syfo.utils.parseDate
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

class InfotrygdKafkaConsumer(
    val env: Environment,
    val accessControlService: AccessControlService,
    val sykepengerMaxDateService: SykepengerMaxDateService,
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.consumers.infotrygd.InfotrygdKafkaConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val objectMapper = createObjectMapper()

    init {
        val kafkaConfig = aivenConsumerProperties(env)
        kafkaListener = KafkaConsumer(kafkaConfig)
        kafkaListener.subscribe(listOf(topicSykepengedagerInfotrygd))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic $topicSykepengedagerInfotrygd")
        while (applicationState.running) {
            kafkaListener.poll(zeroMillis).forEach {
                log.info("Received record from topic $topicSykepengedagerInfotrygd")
                try {
                    val kInfotrygdSykepengedager: KInfotrygdSykepengedager = objectMapper.readValue(it.value())
                    log.info("Infotrygd topic content: ${kInfotrygdSykepengedager.after.F_NR}, ${kInfotrygdSykepengedager.after.IS10_UTBET_TOM},${kInfotrygdSykepengedager.after.IS10_MAX}")

                    val fnr = kInfotrygdSykepengedager.after.F_NR
                    val sykepengerMaxDate = kInfotrygdSykepengedager.after.IS10_MAX
                    val userAccess = accessControlService.getUserAccessStatusByFnr(fnr)

                    if (userAccess.canUserBePhysicallyNotified || userAccess.canUserBeDigitallyNotified) { //TODO: Implement planner and use it's check for access status
                        sykepengerMaxDateService.processNewMaxDate(
                            fnr,
                            parseDate(sykepengerMaxDate),
                            SykepengerMaxDateSource.INFOTRYGD
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
}