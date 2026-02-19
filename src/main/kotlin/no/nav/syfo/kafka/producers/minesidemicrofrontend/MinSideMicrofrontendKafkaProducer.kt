package no.nav.syfo.kafka.producers.minesidemicrofrontend

import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.TOPIC_MIN_SIDE_MICROFRONTEND
import no.nav.syfo.kafka.common.producerProperties
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class MinSideMicrofrontendKafkaProducer(
    val env: Environment,
) {
    private val kafkaConfig = producerProperties(env)
    private val kafkaProducer = KafkaProducer<String, MinSideRecord>(kafkaConfig)

    fun sendRecordToMinSideTopic(minSideRecord: MinSideRecord) {
        kafkaProducer.send(
            ProducerRecord(
                TOPIC_MIN_SIDE_MICROFRONTEND,
                minSideRecord.fnr,
                minSideRecord,
            ),
        )
    }
}
