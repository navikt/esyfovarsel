package no.nav.syfo.kafka.producers.brukernotifikasjoner

import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.producerProperties
import no.nav.tms.varsel.action.EksternKanal
import no.nav.tms.varsel.action.EksternVarslingBestilling
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Tekst
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.builder.VarselActionBuilder
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.net.URL

class BrukernotifikasjonKafkaProducer(
    val env: Environment,
) {
    val brukernotifikasjonerTopic = "min-side.aapen-brukervarsel-v1"
    val kafkaProducer = KafkaProducer<String, String>(producerProperties(env).apply {
        put(
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java),
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        )
    })

    fun sendBeskjed(
        fnr: String,
        content: String,
        uuid: String,
        varselUrl: URL?,
        eksternVarsling: Boolean,
    ) {
        val varsel = createVarsel(
            varseltype = Varseltype.Beskjed,
            uuid = uuid,
            fnr = fnr,
            content = content,
            varselUrl = varselUrl,
            smsVarsling = eksternVarsling,
            smsVarslingTekst = null,
        )

        kafkaProducer.send(ProducerRecord(brukernotifikasjonerTopic, uuid, varsel))
            .get() // Block until record has been sent
    }

    fun sendOppgave(
        fnr: String,
        content: String,
        uuid: String,
        varselUrl: URL,
        smsContent: String?,
    ) {
        val varsel = createVarsel(
            varseltype = Varseltype.Oppgave,
            uuid = uuid,
            fnr = fnr,
            content = content,
            varselUrl = varselUrl,
            smsVarsling = true,
            smsVarslingTekst = smsContent,
        )

        kafkaProducer.send(ProducerRecord(brukernotifikasjonerTopic, uuid, varsel))
            .get() // Block until record has been sent
    }

    fun sendDone(uuid: String) {
        val inaktiverVarsel = VarselActionBuilder.inaktiver {
            varselId = uuid
        }

        kafkaProducer.send(ProducerRecord(brukernotifikasjonerTopic, uuid, inaktiverVarsel)).get()
    }

    private fun createVarsel(
        varseltype: Varseltype,
        uuid: String,
        fnr: String,
        content: String,
        varselUrl: URL?,
        smsVarsling: Boolean,
        smsVarslingTekst: String?,
    ): String {
        val opprettVarsel = VarselActionBuilder.opprett {
            type = varseltype
            varselId = uuid
            sensitivitet = Sensitivitet.High
            ident = fnr
            tekst = Tekst(
                spraakkode = "nb",
                tekst = content,
                default = true
            )
            link = varselUrl.let { it.toString() }
            aktivFremTil = null
            eksternVarsling = if (smsVarsling) {
                EksternVarslingBestilling(
                    prefererteKanaler = listOf(EksternKanal.SMS),
                    smsVarslingstekst = smsVarslingTekst,
                )
            } else null
        }
        return opprettVarsel
    }
}
