package no.nav.syfo.kafka.producers.dittsykefravaer

import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.JacksonKafkaSerializer
import no.nav.syfo.kafka.common.producerProperties
import no.nav.syfo.kafka.common.topicDittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.LukkMelding
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Instant
import java.util.*

class DittSykefravaerMeldingKafkaProducer(
    val env: Environment
) {
    private val kafkaConfig = producerProperties(env).apply {
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonKafkaSerializer::class.java)
    }
    private val kafkaProducer = KafkaProducer<String, DittSykefravaerMelding>(kafkaConfig)

    fun sendMelding(melding: DittSykefravaerMelding): String {
        val uuid = "esyfovarsel-${UUID.randomUUID()}"
        kafkaProducer.send(
            ProducerRecord(
                topicDittSykefravaerMelding,
                uuid,
                melding
            )
        ).get()
        return uuid
    }

    fun ferdigstillMelding(eksternReferanse: String, fnr: String) {
        kafkaProducer.send(
            ProducerRecord(
                topicDittSykefravaerMelding,
                eksternReferanse,
                DittSykefravaerMelding(
                    null,
                    LukkMelding(Instant.now()),
                    fnr
                )
            )
        ).get()
    }
}
