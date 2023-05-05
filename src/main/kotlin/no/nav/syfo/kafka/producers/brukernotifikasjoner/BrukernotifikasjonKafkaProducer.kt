package no.nav.syfo.kafka.producers.brukernotifikasjoner

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
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

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
        val nokkelInput = buildNewNokkelInput(uuid, fnr)
        val beskjedInput = buildNewBeskjed(content, varselUrl)

        val record = ProducerRecord(
            topicBrukernotifikasjonBeskjed,
            nokkelInput,
            beskjedInput
        )

        kafkaBeskjedProducer
            .send(record)
            .get() // Block until record has been sent
    }

    fun sendOppgave(
        fnr: String,
        content: String,
        uuid: String,
        varselUrl: URL
    ) {
        val nokkelInput = buildNewNokkelInput(uuid, fnr)
        val oppgave = buildNewOppgave(content, varselUrl)

        val record = ProducerRecord(
            topicBrukernotifikasjonOppgave,
            nokkelInput,
            oppgave
        )
        kafkaOppgaveProducer
            .send(record)
            .get() // Block until record has been sent
    }

    fun sendDone(uuid: String, fnr: String) {
        val nokkelInput = buildNewNokkelInput(uuid, fnr)
        val doneInput = buildNewDoneInput()

        val record = ProducerRecord(
            topicBrukernotifikasjonDone,
            nokkelInput,
            doneInput
        )

        kafkaDoneProducer
            .send(record)
            .get() // Block until record has been sent
    }

    private fun buildNewDoneInput() = DoneInputBuilder()
        .withTidspunkt(LocalDateTime.now().toLocalDateTimeUTCPlus1())
        .build()

    private fun buildNewNokkelInput(uuid: String, fnr: String): NokkelInput {
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

    private fun LocalDateTime.toLocalDateTimeUTCPlus1() =
        this.atZone(UTCPlus1).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()

    companion object {
        const val sikkerhetsNiva = 4
    }

    enum class MeldingType {
        OPPGAVE, BESKJED, DONE
    }
}
