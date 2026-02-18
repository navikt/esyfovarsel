package no.nav.syfo.kafka.producers.dittsykefravaer

import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.TOPIC_DITT_SYKEEFRAVAER_MELDING
import no.nav.syfo.kafka.common.producerProperties
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.LukkMelding
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Instant

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
                    TOPIC_DITT_SYKEEFRAVAER_MELDING,
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
                    TOPIC_DITT_SYKEEFRAVAER_MELDING,
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
