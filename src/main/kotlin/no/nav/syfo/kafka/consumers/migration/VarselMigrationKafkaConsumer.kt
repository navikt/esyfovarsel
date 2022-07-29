package no.nav.syfo.kafka.consumers.migration

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.*
import no.nav.syfo.kafka.producers.migration.domain.FSSVarsel
import no.nav.syfo.service.migration.MigrationService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration

class VarselMigrationKafkaConsumer(
    val env: Environment,
    val migrationService: MigrationService
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.consumers.migration.VarselMigrationKafkaConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val objectMapper = createObjectMapper()

    init {
        val kafkaConfig = varselMigrationConsumerProperties(env)
        kafkaListener = KafkaConsumer(kafkaConfig)
        kafkaListener.subscribe(listOf(topicVarselMigration))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic: $topicVarselMigration")
        while (applicationState.running) {
            kafkaListener.poll(Duration.ofMillis(0L)).forEach {
                log.info("Received record from topic: [$topicVarselMigration]")
                try {
                    val varselToMigrate = objectMapper.readValue<FSSVarsel>(it.value())
                    migrationService.receiveUnsentVarsel(varselToMigrate)
                } catch (e: IOException) {
                    log.error("Error in [$topicVarselMigration] listener could not parse message: ${e.message}")
                }
                kafkaListener.commitSync()
            }
        }
    }
}
