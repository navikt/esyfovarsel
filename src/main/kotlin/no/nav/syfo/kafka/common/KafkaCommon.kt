package no.nav.syfo.kafka.common

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig.USER_INFO_CONFIG
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import org.apache.kafka.clients.CommonClientConfigs.GROUP_ID_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.*
import org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.config.SaslConfigs.SASL_JAAS_CONFIG
import org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM
import org.apache.kafka.common.config.SslConfigs.*
import java.time.Duration
import java.util.*

const val topicBrukernotifikasjonBeskjed = "min-side.aapen-brukernotifikasjon-beskjed-v1"
const val topicFlexSyketilfellebiter = "flex.syketilfellebiter"
const val topicDineSykmeldteHendelse = "teamsykmelding.dinesykmeldte-hendelser-v2"
const val topicVarselBus = "team-esyfo.varselbus"
const val topicSykepengedagerInfotrygd = "aap.sykepengedager.infotrygd.v1"
const val topicUtbetaling = "tbd.utbetaling"
const val topicDittSykefravaerMelding = "flex.ditt-sykefravaer-melding"

const val JAVA_KEYSTORE = "JKS"
const val PKCS12 = "PKCS12"
const val SSL = "SSL"
const val USER_INFO = "USER_INFO"

val zeroMillis = Duration.ofMillis(0L)

interface KafkaListener {
    suspend fun listen(applicationState: ApplicationState)
}

fun syketilfelleConsumerProperties(env: Environment): Properties {
    return aivenConsumerProperties(env).apply {
        put(GROUP_ID_CONFIG, "esyfovarsel-syketilfelle-group")
    }
}

fun aivenConsumerProperties(env: Environment): Properties {
    val sslConfig = env.kafkaEnv.sslConfig

    return consumerProperties(env).apply {
        put(SECURITY_PROTOCOL_CONFIG, SSL)
        put(SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "") // Disable server host name verification
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

fun consumerProperties(env: Environment): Properties {
    val properties = HashMap<String, String>().apply {
        put(GROUP_ID_CONFIG, "esyfovarsel-group-v04-gcp-v03")
        put(AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(MAX_POLL_RECORDS_CONFIG, "1")
        put(ENABLE_AUTO_COMMIT_CONFIG, "false")
        put(SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
        put(SASL_MECHANISM, "PLAIN")
        put(KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
        put(VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
        put(SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.authEnv.serviceuserUsername}\" password=\"${env.authEnv.serviceuserPassword}\";")
        put(BOOTSTRAP_SERVERS_CONFIG, env.kafkaEnv.bootstrapServersUrl)
    }.toProperties()
    if (!env.appEnv.remote) {
        properties.remove(SECURITY_PROTOCOL_CONFIG)
        properties.remove(SASL_MECHANISM)
    }
    return properties
}

fun producerProperties(env: Environment): Properties {
    val sslConfig = env.kafkaEnv.sslConfig
    val schemaRegistryConfig = env.kafkaEnv.schemaRegistry
    val userinfoConfig = "${schemaRegistryConfig.username}:${schemaRegistryConfig.password}"

    val properties = HashMap<String, String>().apply {
        put(ACKS_CONFIG, "all")
        put(SECURITY_PROTOCOL_CONFIG, SSL)
        put(SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "") // Disable server host name verification
        put(SSL_TRUSTSTORE_TYPE_CONFIG, JAVA_KEYSTORE)
        put(SSL_KEYSTORE_TYPE_CONFIG, PKCS12)
        put(SSL_TRUSTSTORE_LOCATION_CONFIG, sslConfig.truststoreLocation)
        put(SSL_TRUSTSTORE_PASSWORD_CONFIG, sslConfig.credstorePassword)
        put(SSL_KEYSTORE_LOCATION_CONFIG, sslConfig.keystoreLocation)
        put(SSL_KEYSTORE_PASSWORD_CONFIG, sslConfig.credstorePassword)
        put(SSL_KEY_PASSWORD_CONFIG, sslConfig.credstorePassword)
        put(BOOTSTRAP_SERVERS_CONFIG, env.kafkaEnv.aivenBroker)

        put(KEY_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
        put(VALUE_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
        put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryConfig.url)
        put(BASIC_AUTH_CREDENTIALS_SOURCE, USER_INFO)
        put(USER_INFO_CONFIG, userinfoConfig)
        put(BOOTSTRAP_SERVERS_CONFIG, env.kafkaEnv.aivenBroker)

        remove(SASL_MECHANISM)
        remove(SASL_JAAS_CONFIG)
        remove(SASL_MECHANISM)
    }.toProperties()
    if (!env.appEnv.remote) {
        properties.remove(SECURITY_PROTOCOL_CONFIG)
    }
    return properties
}

fun createObjectMapper() = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
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
