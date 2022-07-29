package no.nav.syfo.kafka.producers.migration

import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.JacksonKafkaSerializer
import no.nav.syfo.kafka.common.producerProperties
import no.nav.syfo.kafka.common.topicVarselMigration
import no.nav.syfo.kafka.producers.migration.domain.FSSVarsel
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class VarselMigrationKafkaProducer(
    env: Environment
) {
    private val kafkaConfig = producerProperties(env).apply {
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonKafkaSerializer::class.java)
    }
    private val log = LoggerFactory.getLogger("no.nav.syfo.kafka.producers.migration.VarselMigrationKafkaProducer")

    private val kafkaProducer = KafkaProducer<String, FSSVarsel>(kafkaConfig)

    fun migrateVarsel(varsel: FSSVarsel): Boolean {
        return try {
            kafkaProducer.send(
                ProducerRecord(
                    topicVarselMigration,
                    varsel.uuid,
                    varsel
                )
            ).get()
            true
        } catch (e: Exception) {
            log.info("Error sending varsel with UUID: ${varsel.uuid}")
            false
        }
    }
}
