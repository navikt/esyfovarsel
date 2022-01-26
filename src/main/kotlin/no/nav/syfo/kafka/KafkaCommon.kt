package no.nav.syfo.kafka

import kotlin.collections.HashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.apache.kafka.clients.CommonClientConfigs.GROUP_ID_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.common.config.SaslConfigs.SASL_JAAS_CONFIG
import org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM
import org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.*
import org.apache.kafka.clients.producer.ProducerConfig.*
import org.apache.kafka.common.config.SslConfigs.*
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import no.nav.syfo.*
import java.util.*

const val topicOppfolgingsTilfelle = "aapen-syfo-oppfolgingstilfelle-v1"
const val topicBrukernotifikasjonBeskjed = "aapen-brukernotifikasjon-nyBeskjed-v1"
const val topicFlexSyketilfellebiter = "flex.syketilfellebiter"
const val JAVA_KEYSTORE = "JKS"
const val PKCS12 = "PKCS12"
const val SSL = "SSL"

interface KafkaListener {
    suspend fun listen(applicationState: ApplicationState)
}


fun aivenConsumerProperties(env: Environment) : Properties {
    val sslConfig = env.kafkaEnv.sslConfig

    return consumerProperties(env).apply {
        put(SECURITY_PROTOCOL_CONFIG, SSL)
        put(SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")    // Disable server host name verification
        put(SSL_TRUSTSTORE_TYPE_CONFIG, JAVA_KEYSTORE)
        put(SSL_KEYSTORE_TYPE_CONFIG, PKCS12)
        put(SSL_TRUSTSTORE_LOCATION_CONFIG, sslConfig.truststoreLocation)
        put(SSL_TRUSTSTORE_PASSWORD_CONFIG, sslConfig.credstorePassword)
        put(SSL_KEYSTORE_LOCATION_CONFIG, sslConfig.keystoreLocation)
        put(SSL_KEYSTORE_PASSWORD_CONFIG, sslConfig.credstorePassword)
        put(SSL_KEY_PASSWORD_CONFIG, sslConfig.credstorePassword)
        put(BOOTSTRAP_SERVERS_CONFIG, env.kafkaEnv.aivenBroker)
        remove(SASL_MECHANISM)
        remove(SASL_JAAS_CONFIG)
        remove(SASL_MECHANISM)
    }
}

fun consumerProperties(env: Environment) : Properties {
    val properties = HashMap<String,String>().apply {
        put(GROUP_ID_CONFIG, "esyfovarsel-group-gcp-devtest-b")
        put(AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(MAX_POLL_RECORDS_CONFIG, "1")
        put(ENABLE_AUTO_COMMIT_CONFIG, "false")
        put(SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
        put(SASL_MECHANISM, "PLAIN")
        put(KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
        put(VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
        put(SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.authEnv.serviceuserUsername}\" password=\"${env.authEnv.serviceuserPassword}\";")
        put(BOOTSTRAP_SERVERS_CONFIG, env.kafkaEnv.kafkaBootstrapServersUrl)
    }.toProperties()
    if (!env.appEnv.remote) {
        properties.remove(SECURITY_PROTOCOL_CONFIG)
        properties.remove(SASL_MECHANISM)
    }
    return properties
}

fun producerProperties(env: Environment) : Properties {
    val properties = HashMap<String,String>().apply {
        put(ACKS_CONFIG, "all")
        put(SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
        put(SASL_MECHANISM, "PLAIN")
        put(KEY_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
        put(VALUE_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
        put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, env.kafkaEnv.kafkaSchemaRegistryUrl)
        put(SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.authEnv.serviceuserUsername}\" password=\"${env.authEnv.serviceuserPassword}\";")
        put(BOOTSTRAP_SERVERS_CONFIG, env.kafkaEnv.kafkaBootstrapServersUrl)
    }.toProperties()
    if (!env.appEnv.remote) {
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
