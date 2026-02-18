package no.nav.syfo.kafka.producers.dittsykefravaer

import java.time.Instant
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.producerProperties
import no.nav.syfo.kafka.common.topicDittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.LukkMelding
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class DittSykefravaerMeldingKafkaProducer(
    val env: Environment,
) {
    private val kafkaConfig = producerProperties(env)
    private val kafkaProducer = KafkaProducer<String, DittSykefravaerMelding>(kafkaConfig)

    fun sendMelding(
        melding: DittSykefravaerMelding,
        uuid: String,
    ): String {
        val uuidEkstern = "esyfovarsel-$uuid"
        kafkaProducer
            .send(
                ProducerRecord(
                    topicDittSykefravaerMelding,
                    uuidEkstern,
                    melding,
                ),
            ).get()
        return uuidEkstern
    }

    fun ferdigstillMelding(
        eksternReferanse: String,
        fnr: String,
    ) {
        kafkaProducer
            .send(
                ProducerRecord(
                    topicDittSykefravaerMelding,
                    eksternReferanse,
                    DittSykefravaerMelding(
                        null,
                        LukkMelding(Instant.now()),
                        fnr,
                    ),
                ),
            ).get()
    }
}
