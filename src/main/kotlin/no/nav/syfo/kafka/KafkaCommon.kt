package no.nav.syfo.kafka

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import org.apache.kafka.common.config.SaslConfigs.SASL_JAAS_CONFIG
import java.util.*
import kotlin.collections.HashMap

const val topicOppfolgingsTilfelle = "aapen-syfo-oppfolgingstilfelle-v1"

interface KafkaListener {
    suspend fun listen(applicationState: ApplicationState)
}

fun consumerProperties(env: Environment) : Properties {
    val properties = HashMap<String,String>().apply {
        put("group.id", "esyfovarsel-group-v00")
        put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
        put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
        put("max.poll.records", "1")
        put("auto.offset.reset", "earliest")
        put("enable.auto.commit", "false")
        put("security.protocol", "SASL_SSL")
        put("sasl.mechanism", "PLAIN")
        put(SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.serviceuserUsername}\" password=\"${env.serviceuserPassword}\";")
        put("bootstrap.servers", env.kafkaBootstrapServersUrl)
    }.toProperties()
    if (!env.remote) {
        properties.remove("security.protocol")
        properties.remove("sasl.mechanism")
    }
    return properties
}

suspend fun CoroutineScope.launchKafkaListener(applicationState: ApplicationState, kafkaListener: KafkaListener) {
    launch {
        try {
            kafkaListener.listen(applicationState)
        } finally {
            applicationState.running = false
        }
    }
}
