package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

const val localEnvironmentPropertiesPath = "./src/main/resources/localEnv.json"
const val localTogglesPropertiesPath = "./src/main/resources/localTogglesEnv.json"
const val serviceuserMounthPath = "/var/run/secrets/serviceuser"
val objectMapper = ObjectMapper().registerKotlinModule()

fun testEnvironment(): AppEnvironment =
    localEnvironment()

fun testEnvironment(embeddedKafkaBrokerUrl: String): AppEnvironment =
    localEnvironment()
        .copy(kafkaBootstrapServersUrl = embeddedKafkaBrokerUrl)


private fun localEnvironment(): AppEnvironment {
    return objectMapper.readValue(File(localEnvironmentPropertiesPath), AppEnvironment::class.java)
}

private fun remoteEnvironment(): AppEnvironment {
    return AppEnvironment(
        true,
        getEnvVar("APPLICATION_PORT", "8080").toInt(),
        getEnvVar("APPLICATION_THREADS", "4").toInt(),
        getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        getEnvVar("KAFKA_SCHEMA_REGISTRY_URL"),
        getEnvVar("STS_URL"),
        getEnvVar("SYFOSYKETILFELLE_URL"),
        getEnvVar("PDL_URL"),
        getEnvVar("DKIF_URL"),
        File("$serviceuserMounthPath/username").readText(),
        File("$serviceuserMounthPath/password").readText(),
        getEnvVar("SYFOSMREGISTER_URL"),
        getEnvVar("SYFOSMREGISTER_SCOPE"),
        getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
        getEnvVar("AZURE_APP_CLIENT_ID"),
        getEnvVar("AZURE_APP_CLIENT_SECRET"),
        getDbConfig()
    )
}

fun getDbConfig(): DbEnvironment {
    return DbEnvironment(
        getEnvVar("DATABASE_URL"),
        getEnvVar("DATABASE_NAME", "esyfovarsel"),
        getEnvVar("DB_VAULT_MOUNT_PATH")
    )
}

fun getToggles(): Toggles {
    if (isLocal())
        return objectMapper.readValue(File(localTogglesPropertiesPath), Toggles::class.java)
    return Toggles(
        getEnvVar("TOGGLE_MARKER_VARSLER_SOM_SENDT").tilBoolean(),
        getEnvVar("TOGGLE_SEND_MERVEILEDNING_VARSLER").tilBoolean(),
        getEnvVar("TOGGLE_SEND_AKTIVITETSKRAV_VARSLER").tilBoolean()
    )
}

fun getEnvironment(): AppEnvironment =
    if (isLocal())
        localEnvironment()
    else
        remoteEnvironment()

fun jobEnvironment(): JobEnvironment {
    val (
        remote,
        _,
        _,
        kafkaBootstrapServersUrl,
        kafkaSchemaRegistryUrl,
        stsUrl,
        _,
        pdlUrl,
        dkifUrl,
        serviceuserUsername,
        serviceuserPassword,
        _,
        _,
        _,
        _,
        _,
        dbEnvironment
    ) = if (isLocal()) localEnvironment() else remoteEnvironment()
    return JobEnvironment(
        remote,
        kafkaBootstrapServersUrl,
        kafkaSchemaRegistryUrl,
        stsUrl,
        pdlUrl,
        dkifUrl,
        serviceuserUsername,
        serviceuserPassword,
        dbEnvironment,
        getToggles()
    )
}

data class AppEnvironment(
    override val remote: Boolean,
    val applicationPort: Int,
    val applicationThreads: Int,
    override val kafkaBootstrapServersUrl: String,
    override val kafkaSchemaRegistryUrl: String,
    override val stsUrl: String,
    val syfosyketilfelleUrl: String,
    override val pdlUrl: String,
    override val dkifUrl: String,
    override val serviceuserUsername: String,
    override val serviceuserPassword: String,
    val syfosmregisterUrl: String,
    val syfosmregisterScope: String,
    val aadAccessTokenUrl: String,
    val clientId: String,
    val clientSecret: String,
    override val dbEnvironment: DbEnvironment
): CommonEnvironment

data class JobEnvironment(
    override val remote: Boolean,
    override val kafkaBootstrapServersUrl: String,
    override val kafkaSchemaRegistryUrl: String,
    override val stsUrl: String,
    override val pdlUrl: String,
    override val dkifUrl: String,
    override val serviceuserUsername: String,
    override val serviceuserPassword: String,
    override val dbEnvironment: DbEnvironment,
    val Toggles: Toggles
): CommonEnvironment

interface CommonEnvironment {
    val remote: Boolean
    val kafkaBootstrapServersUrl: String
    val kafkaSchemaRegistryUrl: String
    val stsUrl: String
    val pdlUrl: String
    val dkifUrl: String
    val serviceuserUsername: String
    val serviceuserPassword: String
    val dbEnvironment: DbEnvironment
}

data class DbEnvironment(
    val databaseUrl: String,
    val databaseName: String,
    val dbVaultMountPath: String
)

data class Toggles(
    val markerVarslerSomSendt: Boolean,
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