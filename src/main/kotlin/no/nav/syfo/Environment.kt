package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

const val localAppPropertiesPath = "./src/main/resources/localEnvApp.json"
const val localJobPropertiesPath = "./src/main/resources/localEnvJob.json"
const val serviceuserMounthPath = "/var/run/secrets"
val objectMapper = ObjectMapper().registerKotlinModule()
fun getJobEnv() =
    if (isLocal()) {
        objectMapper.readValue(File(localJobPropertiesPath), JobEnv::class.java)
    } else {
        JobEnv(
            jobTriggerUrl = getEnvVar("ESYFOVARSEL_JOB_TRIGGER_URL"),
            revarsleUnreadAktivitetskrav = getBooleanEnvVar("REVARSLE_UNREAD_AKTIVITETSKRAV"),
            serviceuserUsername = File("$serviceuserMounthPath/username").readText(),
            serviceuserPassword = File("$serviceuserMounthPath/password").readText(),
        )
    }

fun getEnv(): Environment {
    return if (isLocal()) {
        getTestEnv()
    } else {
        Environment(
            AppEnv(
                applicationPort = getEnvVar("APPLICATION_PORT", "8080").toInt(),
                applicationThreads = getEnvVar("APPLICATION_THREADS", "4").toInt(),
                remote = true,
                cluster = getEnvVar("NAIS_CLUSTER_NAME"),
            ),
            AuthEnv(
                serviceuserUsername = File("$serviceuserMounthPath/username").readText(),
                serviceuserPassword = File("$serviceuserMounthPath/password").readText(),
                clientId = getEnvVar("AZURE_APP_CLIENT_ID"),
                clientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
                aadAccessTokenUrl = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
                aadAppWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
                tokenXWellKnownUrl = getEnvVar("TOKEN_X_WELL_KNOWN_URL"),
                tokenXClientId = getEnvVar("TOKEN_X_CLIENT_ID"),
            ),
            UrlEnv(
                syfosmregisterUrl = getEnvVar("SYFOSMREGISTER_URL"),
                syfosmregisterScope = getEnvVar("SYFOSMREGISTER_SCOPE"),
                dkifScope = getEnvVar("DKIF_SCOPE"),
                pdlScope = getEnvVar("PDL_SCOPE"),
                pdlUrl = getEnvVar("PDL_URL"),
                dkifUrl = getEnvVar("DKIF_URL"),
                dialogmoterUrl = getEnvVar("BASE_URL_DIALOGMOTER"),
                oppfolgingsplanerUrl = getEnvVar("BASE_URL_OPPFOLGINGSPLANER"),
                arbeidsgiverNotifikasjonProdusentApiUrl = getEnvVar("AG_NOTIFIKASJON_PRODUSENT_API_URL"),
                arbeidsgiverNotifikasjonProdusentApiScope = getEnvVar("AG_NOTIFIKASJON_PRODUSENT_API_SCOPE"),
                narmestelederUrl = getEnvVar("NARMESTELEDER_URL"),
                narmestelederScope = getEnvVar("NARMESTELEDER_SCOPE"),
                baseUrlDineSykmeldte = getEnvVar("BASE_URL_DINE_SYKMELDTE"),
                dokdistfordelingUrl = getEnvVar("DOKDIST_FORDELING_URL"),
                dokdistfordelingScope = getEnvVar("DOKDIST_FORDELING_SCOPE"),
                istilgangskontrollUrl = getEnvVar("ISTILGANGSKONTROLL_URL"),
                istilgangskontrollScope = getEnvVar("ISTILGANGSKONTROLL_SCOPE"),
                dokumentarkivOppfolgingDocumentsPageUrl = getEnvVar("BASE_URL_DOKUMENTARKIV_OPPFOLGING_DOCUMENTS_PAGE"),
                urlAktivitetskravInfoPage = getEnvVar("URL_AKTIVITETSKRAV_INFO_PAGE"),
                baseUrlNavEkstern = getEnvVar("BASE_URL_NAV_EKSTERN")
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
                    credstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
                ),
            ),
            DbEnv(
                dbHost = getEnvVar("GCP_DB_HOST", "127.0.0.1"),
                dbPort = getEnvVar("GCP_DB_PORT", "5432"),
                dbName = getEnvVar("GCP_DB_DATABASE"),
                dbUsername = getEnvVar("GCP_DB_USERNAME"),
                dbPassword = getEnvVar("GCP_DB_PASSWORD"),
            ),
            ToggleEnv(
                sendAktivitetspliktForhandsvarsel = getBooleanEnvVar("TOGGLE_SEND_AKTIVITETSPLIKT_FORHANDSVARSEL"),
            ),
        )
    }
}

fun getTestEnv() =
    objectMapper.readValue(File(localAppPropertiesPath), Environment::class.java)

data class Environment(
    val appEnv: AppEnv,
    val authEnv: AuthEnv,
    val urlEnv: UrlEnv,
    val kafkaEnv: KafkaEnv,
    val dbEnv: DbEnv,
    val toggleEnv: ToggleEnv,
)

data class AppEnv(
    val applicationPort: Int,
    val applicationThreads: Int,
    val remote: Boolean = false,
    val cluster: String,
)

data class AuthEnv(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val clientId: String,
    val clientSecret: String,
    val aadAccessTokenUrl: String,
    val aadAppWellKnownUrl: String,
    val tokenXWellKnownUrl: String,
    val tokenXClientId: String,
)

data class UrlEnv(
    val syfosmregisterUrl: String,
    val syfosmregisterScope: String,
    val dkifScope: String,
    val pdlScope: String,
    val pdlUrl: String,
    val dkifUrl: String,
    val dialogmoterUrl: String,
    val oppfolgingsplanerUrl: String,
    val arbeidsgiverNotifikasjonProdusentApiUrl: String,
    val arbeidsgiverNotifikasjonProdusentApiScope: String,
    val narmestelederUrl: String,
    val narmestelederScope: String,
    val baseUrlDineSykmeldte: String,
    val dokdistfordelingUrl: String,
    val dokdistfordelingScope: String,
    val istilgangskontrollUrl: String,
    val istilgangskontrollScope: String,
    val dokumentarkivOppfolgingDocumentsPageUrl: String,
    val urlAktivitetskravInfoPage: String,
    val baseUrlNavEkstern: String,
)

data class KafkaEnv(
    var bootstrapServersUrl: String,
    val schemaRegistry: KafkaSchemaRegistryEnv,
    val aivenBroker: String,
    val sslConfig: KafkaSslEnv,
)

data class KafkaSchemaRegistryEnv(
    val url: String,
    val username: String,
    val password: String,
)

data class KafkaSslEnv(
    val truststoreLocation: String,
    val keystoreLocation: String,
    val credstorePassword: String,
)

data class DbEnv(
    var dbHost: String,
    var dbPort: String,
    var dbName: String,
    val dbUsername: String = "",
    val dbPassword: String = "",
)

data class ToggleEnv(
    val sendAktivitetspliktForhandsvarsel: Boolean,
)

data class JobEnv(
    val jobTriggerUrl: String,
    val revarsleUnreadAktivitetskrav: Boolean,
    val serviceuserUsername: String,
    val serviceuserPassword: String,
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun isLocal(): Boolean = getEnvVar("KTOR_ENV", "local") == "local"

fun isJob(): Boolean = getBooleanEnvVar("JOB")

fun getBooleanEnvVar(varName: String) = System.getenv(varName).toBoolean()

const val DEV_GCP = "dev-gcp"

fun Environment.isDevGcp() = DEV_GCP == appEnv.cluster
