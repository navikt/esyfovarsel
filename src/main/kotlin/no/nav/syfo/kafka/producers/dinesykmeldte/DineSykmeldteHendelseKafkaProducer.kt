package no.nav.syfo.kafka.producers.dinesykmeldte

import java.time.OffsetDateTime
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.producerProperties
import no.nav.syfo.kafka.common.topicDineSykmeldteHendelse
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteHendelse
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.FerdigstillHendelse
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.OpprettHendelse
import no.nav.syfo.utils.norwegianOffsetDateTime
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class DineSykmeldteHendelseKafkaProducer(
    val env: Environment,
) {
    private val kafkaConfig = producerProperties(env)
    private val kafkaProducer = KafkaProducer<String, DineSykmeldteHendelse>(kafkaConfig)

    fun sendVarsel(varsel: DineSykmeldteVarsel) {
        val dineSykmeldteHendelse =
            DineSykmeldteHendelse(
                varsel.id.toString(),
                OpprettHendelse(
                    varsel.ansattFnr,
                    varsel.orgnr,
                    varsel.oppgavetype,
                    varsel.lenke,
                    varsel.tekst,
                    OffsetDateTime.now(),
                    varsel.utlopstidspunkt,
                ),
                null,
            )

        publishHendelseOnTopic(dineSykmeldteHendelse)
    }

    fun ferdigstillVarsel(eksternReferanse: String) {
        val dineSykmeldteHendelse =
            DineSykmeldteHendelse(
                eksternReferanse,
                null,
                FerdigstillHendelse(norwegianOffsetDateTime()),
            )

        publishHendelseOnTopic(dineSykmeldteHendelse)
    }

    private fun publishHendelseOnTopic(dineSykmeldteHendelse: DineSykmeldteHendelse) {
        kafkaProducer
            .send(
                ProducerRecord(
                    topicDineSykmeldteHendelse,
                    dineSykmeldteHendelse.id,
                    dineSykmeldteHendelse,
                ),
            ).get()
    }
}
