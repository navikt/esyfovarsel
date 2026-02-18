package no.nav.syfo.kafka.common

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.Duration
import java.util.Properties
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import org.apache.kafka.clients.CommonClientConfigs.GROUP_ID_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_TYPE_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEY_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG

const val topicDineSykmeldteHendelse = "team-esyfo.dinesykmeldte-hendelser-v2"
const val topicVarselBus = "team-esyfo.varselbus"
const val topicDittSykefravaerMelding = "flex.ditt-sykefravaer-melding"
const val topicMinSideMicrofrontend = "min-side.aapen-microfrontend-v1"
const val topicTestdataReset = "teamsykefravr.testdata-reset"

const val JAVA_KEYSTORE = "JKS"
const val PKCS12 = "PKCS12"
const val SSL = "SSL"

val pollDurationInMillis = Duration.ofMillis(1000L)

interface KafkaListener {
    suspend fun listen(applicationState: ApplicationState)
}

fun commonProperties(env: Environment): Properties =
    Properties().apply {
        put(SECURITY_PROTOCOL_CONFIG, SSL)
        put(BOOTSTRAP_SERVERS_CONFIG, env.kafkaEnv.aivenBroker)
        put(SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "") // Disable server host name verification
        put(SSL_TRUSTSTORE_TYPE_CONFIG, JAVA_KEYSTORE)
        put(SSL_KEYSTORE_TYPE_CONFIG, PKCS12)
        put(SSL_TRUSTSTORE_LOCATION_CONFIG, env.kafkaEnv.sslConfig.truststoreLocation)
        put(SSL_TRUSTSTORE_PASSWORD_CONFIG, env.kafkaEnv.sslConfig.credstorePassword)
        put(SSL_KEYSTORE_LOCATION_CONFIG, env.kafkaEnv.sslConfig.keystoreLocation)
        put(SSL_KEYSTORE_PASSWORD_CONFIG, env.kafkaEnv.sslConfig.credstorePassword)
        put(SSL_KEY_PASSWORD_CONFIG, env.kafkaEnv.sslConfig.credstorePassword)
        if (!env.appEnv.remote) {
            remove(SECURITY_PROTOCOL_CONFIG)
        }
    }

fun consumerProperties(env: Environment): Properties =
    commonProperties(env).apply {
        put(GROUP_ID_CONFIG, "esyfovarsel-group-v04-gcp-v03")
        put(AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(MAX_POLL_RECORDS_CONFIG, "1")
        put(ENABLE_AUTO_COMMIT_CONFIG, "false")
        put(KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
        put(VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
    }

fun producerProperties(env: Environment): Properties =
    commonProperties(env).apply {
        put(ACKS_CONFIG, "all")
        put(KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
        put(VALUE_SERIALIZER_CLASS_CONFIG, JacksonKafkaSerializer::class.java)
    }

fun createObjectMapper() =
    ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

suspend fun launchKafkaListener(
    applicationState: ApplicationState,
    kafkaListener: KafkaListener,
) {
    try {
        kafkaListener.listen(applicationState)
    } finally {
        applicationState.running = false
    }
}
