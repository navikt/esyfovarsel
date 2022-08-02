package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

const val localAppPropertiesPath = "./src/main/resources/localEnvApp.json"
const val localJobPropertiesPath = "./src/main/resources/localEnvJob.json"
const val serviceuserMounthPath = "/var/run/secrets"
val objectMapper = ObjectMapper().registerKotlinModule()

fun getJobEnv() =
    if (isLocal())
        objectMapper.readValue(File(localJobPropertiesPath), JobEnv::class.java)
    else
        JobEnv(
            sendVarsler = getBooleanEnvVar("SEND_VARSLER"),
            jobTriggerUrl = getEnvVar("ESYFOVARSEL_JOB_TRIGGER_URL"),
            serviceuserUsername = File("$serviceuserMounthPath/username").readText(),
            serviceuserPassword = File("$serviceuserMounthPath/password").readText()
        )

fun getEnv(): Environment {
    val dbEnv = if (isGCP())
        DbEnv(
            dbHost = getEnvVar("GCP_DB_HOST", "127.0.0.1"),
            dbPort = getEnvVar("GCP_DB_PORT", "5432"),
            dbName = getEnvVar("GCP_DB_DATABASE"),
            dbUsername = getEnvVar("GCP_DB_USERNAME"),
            dbPassword = getEnvVar("GCP_DB_PASSWORD")
        )
    else
        DbEnv(
            dbHost = getEnvVar("DB_HOST", "127.0.0.1"),
            dbPort = "5432",
            dbName = "esyfovarsel",
            dbCredMounthPath = getEnvVar("DB_VAULT_MOUNT_PATH")
        )

    return if (isLocal())
        getTestEnv()
    else
        Environment(
            AppEnv(
                applicationPort = getEnvVar("APPLICATION_PORT", "8080").toInt(),
                applicationThreads = getEnvVar("APPLICATION_THREADS", "4").toInt(),
                remote = true,
                runningInGCPCluster = isGCP()
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
                dkifScope = getEnvVar("DKIF_SCOPE"),
                pdlScope = getEnvVar("PDL_SCOPE"),
                baseUrlSykInfo = getEnvVar("BASE_URL_SYK_INFO"),
                stsUrl = getEnvVar("STS_URL"),
                pdlUrl = getEnvVar("PDL_URL"),
                dkifUrl = getEnvVar("DKIF_URL"),
                dialogmoterUrl = getEnvVar("BASE_URL_DIALOGMOTER"),
                arbeidsgiverNotifikasjonProdusentApiUrl = getEnvVar("AG_NOTIFIKASJON_PRODUSENT_API_URL"),
                arbeidsgiverNotifikasjonProdusentApiScope = getEnvVar("AG_NOTIFIKASJON_PRODUSENT_API_SCOPE"),
                narmestelederUrl = getEnvVar("NARMESTELEDER_URL"),
                narmestelederScope = getEnvVar("NARMESTELEDER_SCOPE"),
                baseUrlDineSykmeldte = getEnvVar("BASE_URL_DINE_SYKMELDTE"),
                syfomotebehovUrl = getEnvVar("SYFOMOTEBEHOV_URL"),
            ),
            KafkaEnv(
                bootstrapServersUrl = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
                schemaRegistry = KafkaSchemaRegistryEnv(
                    url = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
                    username = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
                    password = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
                ),
                aivenBroker = getEnvVar("KAFKA_BROKERS"),
                KafkaSslEnv(
                    truststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
                    keystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
                    credstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD")
                )
            ),
            dbEnv,
            ToggleEnv(
                sendMerVeiledningVarsler = getBooleanEnvVar("TOGGLE_SEND_MERVEILEDNING_VARSLER"),
                sendAktivitetskravVarsler = getBooleanEnvVar("TOGGLE_SEND_AKTIVITETSKRAV_VARSLER"),
                sendSvarMotebehovVarsler = getBooleanEnvVar("TOGGLE_SEND_SVAR_MOTEBEHOV_VARSLER"),
            )
        )
}

fun getTestEnv() =
    objectMapper.readValue(File(localAppPropertiesPath), Environment::class.java)

fun getTestEnv(embeddedKafkaBrokerUrl: String) =
    getTestEnv().apply { kafkaEnv.bootstrapServersUrl = embeddedKafkaBrokerUrl }

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
    val remote: Boolean = false,
    val runningInGCPCluster: Boolean
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
    val dkifScope: String,
    val pdlScope: String,
    val baseUrlSykInfo: String,
    val stsUrl: String,
    val pdlUrl: String,
    val dkifUrl: String,
    val dialogmoterUrl: String,
    val arbeidsgiverNotifikasjonProdusentApiUrl: String,
    val arbeidsgiverNotifikasjonProdusentApiScope: String,
    val narmestelederUrl: String,
    val narmestelederScope: String,
    val baseUrlDineSykmeldte: String,
    val syfomotebehovUrl: String,
)

data class KafkaEnv(
    var bootstrapServersUrl: String,
    val schemaRegistry: KafkaSchemaRegistryEnv,
    val aivenBroker: String,
    val sslConfig: KafkaSslEnv
)

data class KafkaSchemaRegistryEnv(
    val url: String,
    val username: String,
    val password: String
)

data class KafkaSslEnv(
    val truststoreLocation: String,
    val keystoreLocation: String,
    val credstorePassword: String
)

data class DbEnv(
    var dbHost: String,
    var dbPort: String,
    var dbName: String,
    val dbUsername: String = "",
    val dbPassword: String = "",
    val dbCredMounthPath: String = ""
)

data class ToggleEnv(
    val sendMerVeiledningVarsler: Boolean,
    val sendAktivitetskravVarsler: Boolean,
    val sendSvarMotebehovVarsler: Boolean
)

data class JobEnv(
    val sendVarsler: Boolean,
    val jobTriggerUrl: String,
    val serviceuserUsername: String,
    val serviceuserPassword: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun isGCP(): Boolean = getEnvVar("NAIS_CLUSTER_NAME", "none").contains("gcp")

fun isNotGCP(): Boolean = !isGCP()
fun isLocal(): Boolean = getEnvVar("KTOR_ENV", "local") == "local"

fun isJob(): Boolean = getBooleanEnvVar("JOB")

fun getBooleanEnvVar(varName: String) = System.getenv(varName).toBoolean()
