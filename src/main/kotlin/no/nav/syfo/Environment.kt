package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.util.*

const val localAppPropertiesPath = "./src/main/resources/localAppEnv.json"
const val localJobPropertiesPath = "./src/main/resources/localJobEnv.json"
const val serviceuserMounthPath = "/var/run/secrets/serviceuser"
val objectMapper = ObjectMapper().registerKotlinModule()

fun testEnvironment(): AppEnvironment =
    appEnvironment()

fun testEnvironment(embeddedKafkaBrokerUrl: String): AppEnvironment =
    objectMapper.readValue(File(localAppPropertiesPath), AppEnvironment::class.java).apply { commonEnv.kafkaBootstrapServersUrl = embeddedKafkaBrokerUrl }

private fun remoteCommonEnvironment(): CommonEnvironment {
    return CommonEnvironment(
        remote = true,
        kafkaBootstrapServersUrl = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        kafkaSchemaRegistryUrl = getEnvVar("KAFKA_SCHEMA_REGISTRY_URL"),
        stsUrl = getEnvVar("STS_URL"),
        pdlUrl = getEnvVar("PDL_URL"),
        dkifUrl = getEnvVar("DKIF_URL"),
        serviceuserUsername = File("$serviceuserMounthPath/username").readText(),
        serviceuserPassword = File("$serviceuserMounthPath/password").readText(),
        dbEnvironment = DbEnvironment (
            hostname = getEnvVar("NAIS_DATABASE_ESYFOVARSEL_DB_HOST"),
            port = getEnvVar("NAIS_DATABASE_ESYFOVARSEL_DB_PORT"),
            dbname = getEnvVar("NAIS_DATABASE_ESYFOVARSEL_DB_DATABASE", "esyfovarsel"),
            username = getEnvVar("NAIS_DATABASE_ESYFOVARSEL_DB_USERNAME"),
            password = getEnvVar("NAIS_DATABASE_ESYFOVARSEL_DB_PASSWORD"),
            urlWithCredentials = getEnvVar("NAIS_DATABASE_ESYFOVARSEL_DB_URL"),
        )
    )
}

fun appEnvironment(): AppEnvironment =
    if (isLocal())
        objectMapper.readValue(File(localAppPropertiesPath), AppEnvironment::class.java)
    else
        AppEnvironment(
            applicationPort = getEnvVar("APPLICATION_PORT", "8080").toInt(),
            applicationThreads = getEnvVar("APPLICATION_THREADS", "4").toInt(),
            syfosyketilfelleUrl = getEnvVar("SYFOSYKETILFELLE_URL"),
            syfosmregisterUrl = getEnvVar("SYFOSMREGISTER_URL"),
            syfosmregisterScope = getEnvVar("SYFOSMREGISTER_SCOPE"),
            aadAccessTokenUrl = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
            clientId = getEnvVar("AZURE_APP_CLIENT_ID"),
            clientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
            loginserviceDiscoveryUrl = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
            loginserviceAudience = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE").split(","),
            commonEnv = remoteCommonEnvironment()
        )

fun jobEnvironment(): JobEnvironment =
    if (isLocal())
        objectMapper.readValue(File(localJobPropertiesPath), JobEnvironment::class.java)
    else
        JobEnvironment(
            Toggles(
                getEnvVar("TOGGLE_START_JOBB").tilBoolean(),
                getEnvVar("TOGGLE_SEND_MERVEILEDNING_VARSLER").tilBoolean(),
                getEnvVar("TOGGLE_SEND_AKTIVITETSKRAV_VARSLER").tilBoolean()
            ),
            remoteCommonEnvironment(),
            getEnvVar("BASE_URL_DITT_SYKEFRAVAER"),
            getEnvVar("PROMETHEUS_PUSH_GATEWAY_URL")
        )

data class AppEnvironment(
    val applicationPort: Int,
    val applicationThreads: Int,
    val syfosyketilfelleUrl: String,
    val syfosmregisterUrl: String,
    val syfosmregisterScope: String,
    val aadAccessTokenUrl: String,
    val clientId: String,
    val clientSecret: String,
    val loginserviceDiscoveryUrl: String,
    val loginserviceAudience: List<String>,
    var commonEnv: CommonEnvironment
)

data class JobEnvironment(
    val toggles: Toggles,
    var commonEnv: CommonEnvironment,
    val baseUrlDittSykefravaer: String,
    val prometheusPushGatewayUrl: String
)

data class CommonEnvironment(
    val remote: Boolean,
    var kafkaBootstrapServersUrl: String,
    val kafkaSchemaRegistryUrl: String,
    val stsUrl: String,
    val pdlUrl: String,
    val dkifUrl: String,
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val dbEnvironment: DbEnvironment
)

data class DbEnvironment(
    val hostname: String,
    val port: String,
    val dbname: String,
    val username: String,
    val password: String,
    val urlWithCredentials: String

)

data class Toggles(
    val startJobb: Boolean,
    val sendMerVeiledningVarsler: Boolean,
    val sendAktivitetskravVarsler: Boolean
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun isLocal(): Boolean = getEnvVar("KTOR_ENV", "local") == "local"

fun isJob(): Boolean = getEnvVar("SEND_VARSLER", "NEI") == "JA"

private fun String.tilBoolean(): Boolean {
    return this.uppercase(Locale.getDefault()) == "JA"
}
