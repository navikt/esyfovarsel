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
        getDbConfig(),
        getEnvVar("TOGGLE_MARKER_VARSLER_SOM_SENDT").toBoolean(),
        getEnvVar("TOGGLE_SEND_MERVEILEDNING_VARSLER").toBoolean(),
        getEnvVar("TOGGLE_SEND_AKTIVITETSKRAV_VARSLER").toBoolean()
    )
}

private fun localEnvironment(): JobEnvironment {
    return objectMapper.readValue(File(localEnvironmentPropertiesPath), JobEnvironment::class.java)
}

data class JobEnvironment(
    val dbEnvironment: DbEnvironment,
    val toggleMarkerVarslerSomSendt: Boolean,
    val toggleSendMerVeiledningVarsler: Boolean,
    val toggleSendAktivitetskravVarsler: Boolean
)

fun isJob(): Boolean = getEnvVar("SEND_VARSLER", "NEI") == "JA"
