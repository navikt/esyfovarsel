package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.util.*


const val localAppPropertiesPath = "./src/main/resources/localEnvApp.json"
const val localJobPropertiesPath = "./src/main/resources/localEnvJob.json"
const val serviceuserMounthPath = "/var/run/secrets"
val objectMapper = ObjectMapper().registerKotlinModule()

fun getJobEnv() =
    if (isLocal())
        objectMapper.readValue(File(localJobPropertiesPath), JobEnv::class.java)
    else
        JobEnv(
            sendVarsler = getEnvVar("SEND_VARSLER").tilBoolean(),
            jobTriggerUrl = getEnvVar("ESYFOVARSEL_JOB_TRIGGER_URL"),
            serviceuserUsername = File("$serviceuserMounthPath/username").readText(),
            serviceuserPassword = File("$serviceuserMounthPath/password").readText()
        )

fun getEnv() =
    if (isLocal())
        getTestEnv()
    else
        Environment(
            AppEnv(
                applicationPort = getEnvVar("APPLICATION_PORT", "8080").toInt(),
                applicationThreads = getEnvVar("APPLICATION_THREADS", "4").toInt(),
                remote = true
            ),
            AuthEnv(
                serviceuserUsername = File("$serviceuserMounthPath/username").readText(),
                serviceuserPassword = File("$serviceuserMounthPath/password").readText(),
                clientId = getEnvVar("AZURE_APP_CLIENT_ID"),
                clientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
                aadAccessTokenUrl = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
                loginserviceDiscoveryUrl = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
                loginserviceAudience = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE").split(",")
            ),
            UrlEnv(
                syfosyketilfelleUrl = getEnvVar("SYFOSYKETILFELLE_URL"),
                syfosmregisterUrl = getEnvVar("SYFOSMREGISTER_URL"),
                syfosmregisterScope = getEnvVar("SYFOSMREGISTER_SCOPE"),
                baseUrlDittSykefravaer = getEnvVar("BASE_URL_DITT_SYKEFRAVAER"),
                stsUrl = getEnvVar("STS_URL"),
                pdlUrl = getEnvVar("PDL_URL"),
                dkifUrl = getEnvVar("DKIF_URL")
            ),
            KafkaEnv(
                kafkaBootstrapServersUrl = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
                kafkaSchemaRegistryUrl = getEnvVar("KAFKA_SCHEMA_REGISTRY_URL"),
                aivenBroker = getEnvVar("KAFKA_BROKERS"),
                KafkaSslEnv(
                    truststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
                    keystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
                    credstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD")
                )
            ),
            DbEnv(
                dbHost = getEnvVar("NAIS_DATABASE_ESYFOVARSEL_DB_HOST", "127.0.0.1"),
                dbPort = getEnvVar("NAIS_DATABASE_ESYFOVARSEL_DB_PORT", "5432"),
                dbName = getEnvVar("NAIS_DATABASE_ESYFOVARSEL_DB_DATABASE"),
                dbUsername = getEnvVar("NAIS_DATABASE_ESYFOVARSEL_DB_USERNAME"),
                dbPassword = getEnvVar("NAIS_DATABASE_ESYFOVARSEL_DB_PASSWORD")
            ),
            ToggleEnv(
                sendMerVeiledningVarsler = getEnvVar("TOGGLE_SEND_MERVEILEDNING_VARSLER").tilBoolean(),
                sendAktivitetskravVarsler = getEnvVar("TOGGLE_SEND_AKTIVITETSKRAV_VARSLER").tilBoolean()
            )
)

fun getTestEnv() =
    objectMapper.readValue(File(localAppPropertiesPath), Environment::class.java)

fun getTestEnv(embeddedKafkaBrokerUrl: String) =
    getTestEnv().apply { kafkaEnv.kafkaBootstrapServersUrl = embeddedKafkaBrokerUrl }

data class Environment(
    val appEnv: AppEnv,
    val authEnv: AuthEnv,
    val urlEnv: UrlEnv,
    val kafkaEnv: KafkaEnv,
    val dbEnv: DbEnv,
    val toggleEnv: ToggleEnv
)

data class AppEnv(
    val applicationPort: Int,
    val applicationThreads: Int,
    val remote: Boolean = false
)

data class AuthEnv(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val clientId: String,
    val clientSecret: String,
    val aadAccessTokenUrl: String,
    val loginserviceDiscoveryUrl: String,
    val loginserviceAudience: List<String>
)

data class UrlEnv(
    val syfosyketilfelleUrl: String,
    val syfosmregisterUrl: String,
    val syfosmregisterScope: String,
    val baseUrlDittSykefravaer: String,
    val stsUrl: String,
    val pdlUrl: String,
    val dkifUrl: String
)

data class KafkaEnv(
    var kafkaBootstrapServersUrl: String,
    val kafkaSchemaRegistryUrl: String,
    val aivenBroker: String,
    val sslConfig: KafkaSslEnv
)

data class KafkaSslEnv(
    val truststoreLocation: String,
    val keystoreLocation: String,
    val credstorePassword: String
)

data class DbEnv(
    val dbHost: String,
    val dbPort: String,
    val dbName: String,
    val dbUsername: String,
    val dbPassword: String
)

data class ToggleEnv(
    val sendMerVeiledningVarsler: Boolean,
    val sendAktivitetskravVarsler: Boolean
)

data class JobEnv(
    val sendVarsler: Boolean,
    val jobTriggerUrl: String,
    val serviceuserUsername: String,
    val serviceuserPassword: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun isLocal(): Boolean = getEnvVar("KTOR_ENV", "local") == "local"

fun isJob(): Boolean = getEnvVar("JOB", "NEI") == "JA"

private fun String.tilBoolean(): Boolean {
    return this.toUpperCase(Locale.getDefault()) == "JA"
}
