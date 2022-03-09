package no.nav.syfo.kafka.brukernotifikasjoner

import org.apache.kafka.clients.producer.KafkaProducer
import no.nav.brukernotifikasjon.schemas.builders.BeskjedInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.PreferertKanal
import no.nav.brukernotifikasjon.schemas.input.BeskjedInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.syfo.*
import no.nav.syfo.kafka.producerProperties
import no.nav.syfo.kafka.topicBrukernotifikasjonBeskjed
import org.apache.kafka.clients.producer.ProducerRecord
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId

class BeskjedKafkaProducer(
    val env: Environment
) {
    private val kafkaProducer = KafkaProducer<NokkelInput, BeskjedInput>(producerProperties(env))
    private val UTCPlus1 = ZoneId.of("Europe/Oslo")
    private val appNavn = "esyfovarsel"
    private val namespace = "team-esyfo"
    private val groupingId = "ESYFOVARSEL"

    fun sendBeskjed(fnr: String, content: String, uuid: String) {
        val nokkel = buildNewNokkel(uuid, fnr)
        val beskjed = buildNewBeskjed(fnr, content)

        val record = ProducerRecord(
            topicBrukernotifikasjonBeskjed,
            nokkel,
            beskjed
        )

        kafkaProducer
            .send(record)
            .get()              // Block until record has been sent

    }

    private fun buildNewNokkel(uuid: String, fnr: String): NokkelInput {
        return NokkelInputBuilder()
            .withEventId(uuid)
            .withGrupperingsId(groupingId)
            .withFodselsnummer(fnr)
            .withNamespace(namespace)
            .withAppnavn(appNavn)
            .build()
    }

    private fun buildNewBeskjed(fnr: String, content: String): BeskjedInput {
        return BeskjedInputBuilder()
            .withTidspunkt(LocalDateTime.now(UTCPlus1))
            .withTekst(content)
            .withLink(URL(env.urlEnv.baseUrlDittSykefravaer))
            .withSikkerhetsnivaa(sikkerhetsNiva)
            .withSynligFremTil(null)
            .withEksternVarsling(true)
            .withPrefererteKanaler(PreferertKanal.SMS)
            .build()
    }
    
    companion object {
        val sikkerhetsNiva = 4
    }
}
