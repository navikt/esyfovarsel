package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

const val localEnvironmentPropertiesPath = "./src/main/resources/localEnv.json"

fun remoteEnvironment(): Environment {
    return Environment(
        getEnvVar("APPLICATION_PORT", "8080").toInt(),
        getEnvVar("APPLICATION_THREADS", "4").toInt(),
        getEnvVar("DATABASE_URL"),
        getEnvVar("DATABASE_NAME", "esyfovarsel-db")
    )
}

fun localEnvironment() : Environment {
    val objectMapper = ObjectMapper()
    objectMapper.registerKotlinModule()
    return objectMapper.readValue(File(localEnvironmentPropertiesPath), Environment::class.java)
}

data class Environment(
        val applicationPort: Int,
        val applicationThreads: Int,
        val databaseUrl: String,
        val databaseName: String
)

data class ServiceUserSecrets (
    val username: String = readFileFromPath("/var/run/secrets/serviceuser/username"),
    val password: String = readFileFromPath("/var/run/secrets/serviceuser/password")
)

fun readFileFromPath(path: String) : String {
    return Files.readString(Paths.get(path))
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
