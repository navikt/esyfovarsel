package no.nav.syfo.kafka.producers.dittsykefravaer

import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.JacksonKafkaSerializer
import no.nav.syfo.kafka.common.producerProperties
import no.nav.syfo.kafka.common.topicDittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class DittSykefravaerMeldingKafkaProducer(
    val env: Environment
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer")
    private val kafkaConfig = producerProperties(env).apply {
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonKafkaSerializer::class.java)
    }
    private val kafkaProducer = KafkaProducer<String, DittSykefravaerMelding>(kafkaConfig)

    fun sendMelding(melding: DittSykefravaerMelding) {
        val uuid = "esyfovarsel-${UUID.randomUUID()}"
        kafkaProducer.send(
            ProducerRecord(
                topicDittSykefravaerMelding,
                uuid,
                melding
            )
        ).get()
    }
}
