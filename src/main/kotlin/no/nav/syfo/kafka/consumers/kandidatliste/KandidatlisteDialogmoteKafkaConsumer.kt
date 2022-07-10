package no.nav.syfo.kafka.consumers.kandidatliste

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.*
import no.nav.syfo.kafka.consumers.kandidatliste.domain.KafkaDialogmotekandidatEndring
import no.nav.syfo.service.AccessControl
import no.nav.syfo.service.MotebehovVarselService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

class KandidatlisteDialogmoteKafkaConsumer(
    val env: Environment,
    val accessControl: AccessControl,
    val motebehovVarselService: MotebehovVarselService
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.KandidatlisteDialogmoteKafkaConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val objectMapper = createObjectMapper()

    init {
        val kafkaConfig = aivenConsumerProperties(env)
        kafkaListener = KafkaConsumer(kafkaConfig)
        kafkaListener.subscribe(listOf(topicKandidatliste))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        if (!env.toggleEnv.useKandidatlisteTopic) {
            log.info("KANDIDATLISTE-CONSUMER: Kandidatliste-toggle is disabled")
            return
        }
        log.info("Started listening to topic: $topicKandidatliste")
        while (applicationState.running) {
            kafkaListener.poll(zeroMillis).forEach {
                log.info("KANDIDATLISTE-CONSUMER: Received message with UUID ${it.key()}")
                try {
                    val kandidat: KafkaDialogmotekandidatEndring = objectMapper.readValue(it.value())
                    val kandidatFnr = kandidat.personIdentNumber
                    if (accessControl.canUserBeNotified(kandidatFnr)) {
                        motebehovVarselService.sendVarselTilDialogmoteKandidat(kandidatFnr)
                    }
                } catch (e: IOException) {
                    log.error(
                        "Error in [$topicKandidatliste]-listener: Could not parse message | ${e.message}",
                        e
                    )
                } catch (e: Exception) {
                    log.error(
                        "Exception in [$topicKandidatliste]-listener: ${e.message}",
                        e
                    )
                }
                kafkaListener.commitSync()
            }
        }
    }
}
