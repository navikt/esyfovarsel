package no.nav.syfo.kafka.consumers.brukernotifikasjoner

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.KafkaListener
import no.nav.syfo.kafka.common.consumerProperties
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.common.minsideVarselHendelseTopic
import no.nav.syfo.kafka.common.pollDurationInMillis
import no.nav.syfo.service.MinSideVarselHendelseService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BrukernotifikasjonKafkaConsumer(
    val env: Environment,
    val minSideVarselHendelseService: MinSideVarselHendelseService,
): KafkaListener {
    private val log: Logger = LoggerFactory.getLogger(BrukernotifikasjonKafkaConsumer::class.qualifiedName)
    private val kafkaListener: KafkaConsumer<String, String>
    private val objectMapper = createObjectMapper()


    init {
        val kafkaConfig = consumerProperties(env)
        kafkaListener = KafkaConsumer(kafkaConfig)
        kafkaListener.subscribe(listOf(minsideVarselHendelseTopic))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic $minsideVarselHendelseTopic")

        while (applicationState.running) {
            try {
                    kafkaListener.poll(pollDurationInMillis).forEach { minSideVarselHendelseService.processRecord(it) }
                    kafkaListener.commitSync()
            } catch (e: Exception) {
                log.error(
                    "Exception in [$minsideVarselHendelseTopic]-listener: ${e.message}",
                    e
                )
                kafkaListener.commitSync()
            }
        }
    }
}