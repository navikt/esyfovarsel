package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

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
        true,
        getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        getEnvVar("KAFKA_SCHEMA_REGISTRY_URL"),
        getEnvVar("STS_URL"),
        getEnvVar("PDL_URL"),
        getEnvVar("DKIF_URL"),
        File("$serviceuserMounthPath/username").readText(),
        File("$serviceuserMounthPath/password").readText(),
        DbEnvironment (
            getEnvVar("DATABASE_URL"),
            getEnvVar("DATABASE_NAME", "esyfovarsel"),
            getEnvVar("DB_VAULT_MOUNT_PATH")
        )
    )
}

fun appEnvironment(): AppEnvironment =
    if (isLocal())
        objectMapper.readValue(File(localAppPropertiesPath), AppEnvironment::class.java)
    else
        AppEnvironment(
            getEnvVar("APPLICATION_PORT", "8080").toInt(),
            getEnvVar("APPLICATION_THREADS", "4").toInt(),
            getEnvVar("SYFOSYKETILFELLE_URL"),
            getEnvVar("SYFOSMREGISTER_URL"),
            getEnvVar("SYFOSMREGISTER_SCOPE"),
            getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
            getEnvVar("AZURE_APP_CLIENT_ID"),
            getEnvVar("AZURE_APP_CLIENT_SECRET"),
            remoteCommonEnvironment()
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
    val databaseUrl: String,
    val databaseName: String,
    val dbVaultMountPath: String
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
    return this.toUpperCase() == "JA"
}
