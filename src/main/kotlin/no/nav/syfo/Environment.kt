package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

const val localEnvironmentPropertiesPath = "./src/main/resources/localEnv.json"
const val serviceuserMounthPath = "/var/run/secrets/serviceuser"
val objectMapper = ObjectMapper().registerKotlinModule()

fun testEnviornment(): Environment =
    localEnvironment()

fun testEnviornment(embeddedKafkaBrokerUrl: String): Environment =
    localEnvironment()
        .copy(kafkaBootstrapServersUrl = embeddedKafkaBrokerUrl)

fun getEnvironment(): Environment =
    if (isLocal())
        localEnvironment()
    else
        remoteEnvironment()

private fun remoteEnvironment(): Environment {
    return Environment(
        true,
        getEnvVar("APPLICATION_PORT", "8080").toInt(),
        getEnvVar("APPLICATION_THREADS", "4").toInt(),
        getEnvVar("DATABASE_URL"),
        getEnvVar("DATABASE_NAME", "esyfovarsel"),
        getEnvVar("DB_VAULT_MOUNT_PATH"),
        getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
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
        getEnvVar("AZURE_APP_CLIENT_SECRET")
    )
}

private fun localEnvironment(): Environment {
    return objectMapper.readValue(File(localEnvironmentPropertiesPath), Environment::class.java)
}

data class Environment(
    val remote: Boolean,
    val applicationPort: Int,
    val applicationThreads: Int,
    val databaseUrl: String,
    val databaseName: String,
    val dbVaultMountPath: String,
    val kafkaBootstrapServersUrl: String,
    val stsUrl: String,
    val syfosyketilfelleUrl: String,
    val pdlUrl: String,
    val dkifUrl: String,
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val syfosmregisterUrl: String,
    val syfosmregisterScope: String,
    val aadAccessTokenUrl: String,
    val clientId: String,
    val clientSecret: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

private fun isLocal(): Boolean = getEnvVar("KTOR_ENV", "local") == "local"
