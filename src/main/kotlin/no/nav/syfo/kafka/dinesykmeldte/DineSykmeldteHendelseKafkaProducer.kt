package no.nav.syfo.kafka.dinesykmeldte

import no.nav.syfo.Environment
import no.nav.syfo.kafka.dinesykmeldte.domain.DineSykmeldteHendelse
import no.nav.syfo.kafka.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.dinesykmeldte.domain.OpprettHendelse
import no.nav.syfo.kafka.producerPropertiesDineSykmeldte
import no.nav.syfo.kafka.topicDineSykmeldteHendelse
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.*
import java.time.OffsetDateTime

class DineSykmeldteHendelseKafkaProducer(
    val env: Environment
) {
    private val kafkaProducer = KafkaProducer<String, DineSykmeldteHendelse>(producerPropertiesDineSykmeldte(env))

    fun sendHendelse(varsel: DineSykmeldteVarsel) {
        val dineSykmeldteHendelse = DineSykmeldteHendelse(
            "${UUID.randomUUID()}",
            OpprettHendelse(
                varsel.ansattFnr,
                varsel.orgnr,
                varsel.oppgavetype,
                varsel.lenke,
                varsel.tekst,
                OffsetDateTime.now(),
                varsel.utlopstidspunkt
            ),
            null
        )

        kafkaProducer.send(
            ProducerRecord(
                topicDineSykmeldteHendelse,
                dineSykmeldteHendelse.id,
                dineSykmeldteHendelse
            )
        ).get()
    }
}
