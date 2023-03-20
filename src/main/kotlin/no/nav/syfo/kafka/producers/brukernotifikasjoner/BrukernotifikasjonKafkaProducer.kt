package no.nav.syfo.kafka.producers.brukernotifikasjoner

import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import no.nav.brukernotifikasjon.schemas.builders.BeskjedInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.DoneInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.OppgaveInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.PreferertKanal
import no.nav.brukernotifikasjon.schemas.input.BeskjedInput
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.brukernotifikasjon.schemas.input.OppgaveInput
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.producerProperties
import no.nav.syfo.kafka.common.topicBrukernotifikasjonBeskjed
import no.nav.syfo.kafka.common.topicBrukernotifikasjonDone
import no.nav.syfo.kafka.common.topicBrukernotifikasjonOppgave
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BrukernotifikasjonKafkaProducer(
    val env: Environment
) {
    private val kafkaBeskjedProducer = KafkaProducer<NokkelInput, BeskjedInput>(producerProperties(env))
    private val kafkaOppgaveProducer = KafkaProducer<NokkelInput, OppgaveInput>(producerProperties(env))
    private val kafkaDoneProducer = KafkaProducer<NokkelInput, DoneInput>(producerProperties(env))

    private val UTCPlus1 = ZoneId.of("Europe/Oslo")
    private val appNavn = "esyfovarsel"
    private val namespace = "team-esyfo"
    private val groupingId = "ESYFOVARSEL"

    fun sendBeskjed(fnr: String, content: String, uuid: String, varselUrl: URL) {
        val nokkel = buildNewNokkel(uuid, fnr)
        val beskjed = buildNewBeskjed(content, varselUrl)

        val record = ProducerRecord(
            topicBrukernotifikasjonBeskjed,
            nokkel,
            beskjed
        )

        kafkaBeskjedProducer
            .send(record)
            .get() // Block until record has been sent
    }

    fun sendOppgave(
        fnr: String, content: String, uuid: String, varselUrl: URL
    ) {
        val nokkel = buildNewNokkel(uuid, fnr)
        val oppgave = buildNewOppgave(content, varselUrl)

        val record = ProducerRecord(
            topicBrukernotifikasjonOppgave,
            nokkel,
            oppgave
        )
        kafkaOppgaveProducer
            .send(record)
            .get() // Block until record has been sent
    }

    fun sendDone(
        fnr: String, uuid: String
    ) {
        val nokkel = buildNewNokkel(uuid, fnr)
        val done = buildNewDone()
        val record = ProducerRecord(
            topicBrukernotifikasjonDone,
            nokkel,
            done
        )
        kafkaDoneProducer
            .send(record)
            .get() // Block until record has been sent
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

    private fun buildNewBeskjed(content: String, varselUrl: URL): BeskjedInput {
        return BeskjedInputBuilder()
            .withTidspunkt(LocalDateTime.now(UTCPlus1))
            .withTekst(content)
            .withLink(varselUrl)
            .withSikkerhetsnivaa(sikkerhetsNiva)
            .withSynligFremTil(null)
            .withEksternVarsling(true)
            .withPrefererteKanaler(PreferertKanal.SMS)
            .build()
    }

    fun buildNewOppgave(content: String, varselUrl: URL): OppgaveInput = OppgaveInputBuilder()
        .withTidspunkt(LocalDateTime.now(UTCPlus1))
        .withTekst(content)
        .withLink(varselUrl)
        .withSikkerhetsnivaa(sikkerhetsNiva)
        .withEksternVarsling(true)
        .withPrefererteKanaler(PreferertKanal.SMS)
        .build()

    fun buildNewDone(): DoneInput = DoneInputBuilder()
        .withTidspunkt(LocalDateTime.now(UTCPlus1))
        .build()

    companion object {
        val sikkerhetsNiva = 4
    }

    enum class MeldingType {
        OPPGAVE, BESKJED, DONE
    }
}
