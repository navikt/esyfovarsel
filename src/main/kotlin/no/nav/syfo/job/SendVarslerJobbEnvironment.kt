package no.nav.syfo.job

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.DbEnvironment
import no.nav.syfo.getDbConfig
import no.nav.syfo.getEnvVar
import no.nav.syfo.isLocal
import java.io.File

const val localEnvironmentPropertiesPath = "./src/main/resources/localEnvJob.json"
val objectMapper = ObjectMapper().registerKotlinModule()

fun getJobEnvironment(): JobEnvironment =
    if (isLocal())
        localEnvironment()
    else
        remoteEnvironment()

private fun remoteEnvironment(): JobEnvironment {
    return JobEnvironment(
        getDbConfig()
    )
}

private fun localEnvironment(): JobEnvironment {
    return objectMapper.readValue(File(localEnvironmentPropertiesPath), JobEnvironment::class.java)
}

data class JobEnvironment(
    val dbEnvironment: DbEnvironment
)

fun isJob(): Boolean = getEnvVar("SEND_VARSLER", "NEI") == "JA"
