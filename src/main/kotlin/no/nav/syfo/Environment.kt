package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

const val localEnvironmentPropertiesPath = "./src/main/resources/localEnv.json"
const val serviceuserMounthPath = "/var/run/secrets/serviceuser"
val objectMapper = ObjectMapper().registerKotlinModule()


fun getEnvironment(): Environment =
    if (isLocal())
        localEnvironment()
    else
        remoteEnvironment()

private fun remoteEnvironment(): Environment {
    return Environment(
        getEnvVar("APPLICATION_PORT", "8080").toInt(),
        getEnvVar("APPLICATION_THREADS", "4").toInt(),
        getEnvVar("DATABASE_URL"),
        getEnvVar("DATABASE_NAME", "esyfovarsel"),
        getEnvVar("DB_VAULT_MOUNT_PATH"),
        getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        getEnvVar("SYFOSYKETILFELLE_URL"),
        File("$serviceuserMounthPath/username").readText(),
        File("$serviceuserMounthPath/password").readText(),
        getEnvVar("STS_URL")
    )
}

private fun localEnvironment() : Environment {
    return objectMapper.readValue(File(localEnvironmentPropertiesPath), Environment::class.java)
}

data class Environment(
        val applicationPort: Int,
        val applicationThreads: Int,
        val databaseUrl: String,
        val databaseName: String,
        val dbVaultMountPath: String,
        val kafkaBootstrapServersUrl: String,
        val syfosyketilfelleUrl: String,
        val serviceuserUsername: String,
        val serviceuserPassword: String,
        val stsUrl: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

private fun isLocal(): Boolean = getEnvVar("KTOR_ENV", "local") == "local"
