package no.nav.syfo.kafka

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import org.apache.kafka.clients.CommonClientConfigs.GROUP_ID_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.*
import org.apache.kafka.common.config.SaslConfigs.SASL_JAAS_CONFIG
import org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM
import java.util.*
import kotlin.collections.HashMap

const val topicOppfolgingsTilfelle = "aapen-syfo-oppfolgingstilfelle-v1"

interface KafkaListener {
    suspend fun listen(applicationState: ApplicationState)
}

fun consumerProperties(env: Environment) : Properties {
    val properties = HashMap<String,String>().apply {
        put(GROUP_ID_CONFIG, "esyfovarsel-group-v00")
        put(AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(MAX_POLL_RECORDS_CONFIG, "1")
        put(ENABLE_AUTO_COMMIT_CONFIG, "false")
        put(SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
        put(SASL_MECHANISM, "PLAIN")
        put(KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
        put(VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
        put(SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.serviceuserUsername}\" password=\"${env.serviceuserPassword}\";")
        put(BOOTSTRAP_SERVERS_CONFIG, env.kafkaBootstrapServersUrl)
    }.toProperties()
    if (!env.remote) {
        properties.remove(SECURITY_PROTOCOL_CONFIG)
        properties.remove(SASL_MECHANISM)
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
