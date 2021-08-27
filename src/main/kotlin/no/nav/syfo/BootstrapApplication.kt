package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.consumer.DkifConsumer
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.consumer.SykmeldingerConsumer
import no.nav.syfo.db.*
import no.nav.syfo.job.JobEnvironment
import no.nav.syfo.job.SendVarslerJobb
import no.nav.syfo.job.getJobEnvironment
import no.nav.syfo.job.isJob
import no.nav.syfo.kafka.launchKafkaListener
import no.nav.syfo.kafka.oppfolgingstilfelle.OppfolgingstilfelleKafkaConsumer
import no.nav.syfo.service.AccessControl
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.varsel.AktivitetskravVarselPlanner
import no.nav.syfo.varsel.MerVeiledningVarselPlanner
import no.nav.syfo.varsel.VarselSender
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
        val env: JobEnvironment = getJobEnvironment()
        database = initDb(env.dbEnvironment)
        val jobb = SendVarslerJobb(
            database,
            VarselSender(),
            env.toggleMarkerVarslerSomSendt,
            env.toggleSendMerVeiledningVarsler,
            env.toggleSendAktivitetskravVarsler
        )
        jobb.sendVarsler()
    } else {
        val env: Environment = getEnvironment()
        val server = embeddedServer(Netty, applicationEngineEnvironment {
            log = LoggerFactory.getLogger("ktor.application")
            config = HoconApplicationConfig(ConfigFactory.load())

            connector {
                port = env.applicationPort
            }

            module {
                state.running = true
                database = initDb(env.dbEnvironment)
                serverModule()
                kafkaModule(env)
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

fun Application.serverModule() {

    routing {
        registerNaisApi(state)
    }

    state.initialized = true
}

@KtorExperimentalAPI
fun Application.kafkaModule(env: Environment) {

    runningRemotely {
        val stsConsumer = StsConsumer(env)
        val azureAdTokenConsumer = AzureAdTokenConsumer(env)
        val pdlConsumer = PdlConsumer(env, stsConsumer)
        val dkifConsumer = DkifConsumer(env, stsConsumer)
        val oppfolgingstilfelleConsumer = SyfosyketilfelleConsumer(env, stsConsumer)
        val accessControl = AccessControl(pdlConsumer, dkifConsumer)
        val sykmeldingerConsumer = SykmeldingerConsumer(env, azureAdTokenConsumer)
        val sykmeldingService = SykmeldingService(sykmeldingerConsumer)

        val oppfolgingstilfelleKafkaConsumer = OppfolgingstilfelleKafkaConsumer(env, accessControl)
            .addPlanner(AktivitetskravVarselPlanner(database, oppfolgingstilfelleConsumer, sykmeldingService))
            .addPlanner(MerVeiledningVarselPlanner(database, oppfolgingstilfelleConsumer))

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
