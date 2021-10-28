package no.nav.syfo.kafka

import kotlin.collections.HashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.nav.syfo.ApplicationState
import org.apache.kafka.clients.CommonClientConfigs.GROUP_ID_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.common.config.SaslConfigs.SASL_JAAS_CONFIG
import org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM
import org.apache.kafka.clients.consumer.ConsumerConfig.*
import org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import no.nav.syfo.CommonEnvironment
import java.util.*

const val topicOppfolgingsTilfelle = "aapen-syfo-oppfolgingstilfelle-v1"
const val topicBrukernotifikasjonBeskjed = "aapen-brukernotifikasjon-nyBeskjed-v1"

interface KafkaListener {
    suspend fun listen(applicationState: ApplicationState)
}

fun consumerProperties(env: CommonEnvironment) : Properties {
    val properties = HashMap<String,String>().apply {
        put(GROUP_ID_CONFIG, "esyfovarsel-group-v0005")
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

fun producerProperties(env: CommonEnvironment) : Properties {
    val properties = HashMap<String,String>().apply {
        put(ACKS_CONFIG, "all")
        put(SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
        put(SASL_MECHANISM, "PLAIN")
        put(KEY_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
        put(VALUE_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
        put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, env.kafkaSchemaRegistryUrl)
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
