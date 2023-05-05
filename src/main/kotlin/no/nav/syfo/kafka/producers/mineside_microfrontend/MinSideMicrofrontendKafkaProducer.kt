package no.nav.syfo.kafka.producers.mineside_microfrontend

import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.JacksonKafkaSerializer
import no.nav.syfo.kafka.common.producerProperties
import no.nav.syfo.kafka.common.topicMinSideMicrofrontend
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord

class MinSideMicrofrontendKafkaProducer(
    val env: Environment
) {
    private val kafkaConfig = producerProperties(env).apply {
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonKafkaSerializer::class.java)
    }

    private val kafkaProducer = KafkaProducer<String, MinSideRecord>(kafkaConfig)

    fun sendRecordToMinSideTopic(minSideRecord: MinSideRecord) {
        kafkaProducer.send(
            ProducerRecord(
                topicMinSideMicrofrontend,
                minSideRecord.fnr,
                minSideRecord
            )
        )
    }
}
