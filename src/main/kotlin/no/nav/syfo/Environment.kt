package no.nav.syfo

import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
        val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
        val serviceUserSecrets: ServiceUserSecrets = ServiceUserSecrets()
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
