package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.jackson.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import no.nav.syfo.auth.*
import no.nav.syfo.consumer.*
import no.nav.syfo.db.*
import no.nav.syfo.job.SendVarslerJobb
import no.nav.syfo.kafka.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.launchKafkaListener
import no.nav.syfo.kafka.oppfolgingstilfelle.OppfolgingstilfelleKafkaConsumer
import no.nav.syfo.metrics.registerPrometheusApi
import no.nav.syfo.metrics.withPrometheus
import no.nav.syfo.service.AccessControl
import no.nav.syfo.service.SendVarselService
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.service.VarselSendtService
import no.nav.syfo.varsel.AktivitetskravVarselPlanner
import no.nav.syfo.varsel.MerVeiledningVarselPlanner
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = false, var initialized: Boolean = false)

val state: ApplicationState = ApplicationState()
val backgroundTasksContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
lateinit var database: DatabaseInterface

@KtorExperimentalAPI
fun main() {
    if (isJob()) {
        val env = jobEnvironment()

        if(env.toggles.startJobb) {
            val stsConsumer = StsConsumer(env.commonEnv)
            val pdlConsumer = PdlConsumer(env.commonEnv, stsConsumer)
            val dkifConsumer = DkifConsumer(env.commonEnv, stsConsumer)

            val accessControl = AccessControl(pdlConsumer, dkifConsumer)
            val beskjedKafkaProducer = BeskjedKafkaProducer(env.commonEnv, env.baseUrlDittSykefravaer)
            val sendVarselService = SendVarselService(beskjedKafkaProducer, accessControl)

            database = initDb(env.commonEnv.dbEnvironment)

            val jobb = SendVarslerJobb(
                database,
                sendVarselService,
                env.toggles
            )

            withPrometheus(env.prometheusPushGatewayUrl) {
                jobb.sendVarsler()
            }
        } else {
            LoggerFactory.getLogger("no.nav.syfo.BootstrapApplication").info("Jobb togglet av")
        }

    } else {
        val env: AppEnvironment = appEnvironment()
        val server = embeddedServer(Netty, applicationEngineEnvironment {
            log = LoggerFactory.getLogger("ktor.application")
            config = HoconApplicationConfig(ConfigFactory.load())
            database = initDb(env.commonEnv.dbEnvironment)

            val stsConsumer = getStsConsumer(env.commonEnv)
            val pdlConsumer = getPdlConsumer(env.commonEnv, stsConsumer)
            val dkifConsumer = DkifConsumer(env.commonEnv, stsConsumer)
            val oppfolgingstilfelleConsumer = getSyfosyketilfelleConsumer(env, stsConsumer)
            val azureAdTokenConsumer = AzureAdTokenConsumer(env)
            val sykmeldingerConsumer = SykmeldingerConsumer(env, azureAdTokenConsumer)

            val accessControl = AccessControl(pdlConsumer, dkifConsumer)
            val sykmeldingService = SykmeldingService(sykmeldingerConsumer)
            val varselSendtService = VarselSendtService(pdlConsumer, oppfolgingstilfelleConsumer, database)

            connector {
                port = env.applicationPort
            }

            module {
                state.running = true

                serverModule(
                    env,
                    varselSendtService

                )
                kafkaModule(
                    env,
                    accessControl,
                    oppfolgingstilfelleConsumer,
                    sykmeldingService,
                    varselSendtService
                )
            }
        })
        Runtime.getRuntime().addShutdownHook(Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        })

        server.start(wait = false)
    }
}

fun initDb(dbEnv: DbEnvironment): Database = if(isLocal()) localDatabase(dbEnv) else remoteDatabase(dbEnv)

private fun localDatabase(env: DbEnvironment): Database = LocalDatabase(
    DbConfig(
        jdbcUrl = env.databaseUrl,
        databaseName = env.databaseName,
        password = "password",
        username = "esyfovarsel-admin",
        remote = false
    )
)

private fun remoteDatabase(env: DbEnvironment): Database = RemoteDatabase(
    DbConfig(
        jdbcUrl = env.databaseUrl,
        databaseName = env.databaseName,
        dbCredMountPath = env.dbVaultMountPath
    )
)

private fun getStsConsumer(env: CommonEnvironment): StsConsumer {
    if (isLocal()) {
        return LocalStsConsumer(env)
    }
    return StsConsumer(env)
}

private fun getPdlConsumer(env: CommonEnvironment, stsConsumer: StsConsumer): PdlConsumer {
    if (isLocal()) {
        return LocalPdlConsumer(env, stsConsumer)
    }
    return PdlConsumer(env, stsConsumer)
}

private fun getSyfosyketilfelleConsumer(env: AppEnvironment, stsConsumer: StsConsumer): SyfosyketilfelleConsumer {
    if (isLocal()) {
        return LocalSyfosyketilfelleConsumer(env, stsConsumer)
    }
    return SyfosyketilfelleConsumer(env, stsConsumer)
}

fun Application.serverModule(
    appEnv: AppEnvironment,
    varselSendtService: VarselSendtService
) {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    runningRemotely {
        setupRoutesWithAuthentication(varselSendtService, appEnv)
    }

    runningLocally {
        setupRoutesWithoutAuthentication(varselSendtService)
    }

    state.initialized = true
}

@KtorExperimentalAPI
fun Application.kafkaModule(
    env: AppEnvironment,
    accessControl: AccessControl,
    oppfolgingstilfelleConsumer: SyfosyketilfelleConsumer,
    sykmeldingService: SykmeldingService,
    varselSendtService: VarselSendtService
) {
    runningRemotely {
        val oppfolgingstilfelleKafkaConsumer = OppfolgingstilfelleKafkaConsumer(env, accessControl)
            .addPlanner(AktivitetskravVarselPlanner(database, oppfolgingstilfelleConsumer, sykmeldingService))
            .addPlanner(MerVeiledningVarselPlanner(database, oppfolgingstilfelleConsumer, varselSendtService))

        launch(backgroundTasksContext) {
            launchKafkaListener(
                state,
                oppfolgingstilfelleKafkaConsumer
            )
        }
    }
}

@KtorExperimentalAPI
val Application.envKind
    get() = environment.config.property("ktor.environment").getString()

@KtorExperimentalAPI
fun Application.runningRemotely(block: () -> Unit) {
    if (envKind == "remote") block()
}

fun Application.runningLocally(block: () -> Unit) {
    if (envKind == "local") block()
}
