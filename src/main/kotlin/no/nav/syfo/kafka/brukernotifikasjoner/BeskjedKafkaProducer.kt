package no.nav.syfo.kafka.brukernotifikasjoner

import org.apache.kafka.clients.producer.KafkaProducer
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.builders.BeskjedBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.PreferertKanal
import no.nav.syfo.CommonEnvironment
import no.nav.syfo.kafka.producerProperties
import no.nav.syfo.kafka.topicBrukernotifikasjonBeskjed
import org.apache.kafka.clients.producer.ProducerRecord
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class BeskjedKafkaProducer(
    val env: CommonEnvironment,
    val baseUrlSykefravaer: String
) {
    private val kafkaProducer = KafkaProducer<Nokkel, Beskjed>(producerProperties(env))
    private val UTCPlus1 = ZoneId.of("Europe/Oslo")
    private val groupingId = "ESYFOVARSEL"

    fun sendBeskjed(fnr: String, content: String, uuid: String) {
        val nokkel = buildNewNokkel(uuid)
        val beskjed = buildNewBeskjed(fnr, content)

        val record = ProducerRecord<Nokkel, Beskjed>(
            topicBrukernotifikasjonBeskjed,
            nokkel,
            beskjed
        )

        // TODO: Uncomment here as soon as we are ready to go live

        /*
        kafkaProducer
            .send(record)
            .get()              // Block until record has been sent

        */
    }

    private fun buildNewNokkel(uuid: String): Nokkel {
        return NokkelBuilder()
            .withSystembruker(env.serviceuserUsername)
            .withEventId(uuid)
            .build()
    }

    private fun buildNewBeskjed(fnr: String, content: String): Beskjed {
        return BeskjedBuilder()
            .withFodselsnummer(fnr)
            .withEksternVarsling(true)
            .withTidspunkt(LocalDateTime.now(UTCPlus1))
            .withTekst(content)
            .withLink(URL(baseUrlSykefravaer))
            .withSikkerhetsnivaa(sikkerhetsNiva)
            .withPrefererteKanaler(PreferertKanal.SMS)
            .withGrupperingsId(groupingId)
            .build()
    }
    
    companion object {
        val sikkerhetsNiva = 4
    }
}
