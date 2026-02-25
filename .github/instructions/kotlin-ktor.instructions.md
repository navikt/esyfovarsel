---
applyTo: "**/*.kt"
---

# Kotlin/Ktor Development Standards

## Application Structure

esyfovarsel uses manual dependency wiring in `BootstrapApplication.kt` — no DI framework. All dependencies are constructed and passed through constructors.

```kotlin
// Dependencies are manually wired in BootstrapApplication
val azureAdTokenConsumer = AzureAdTokenConsumer(env.authEnv)
val dkifConsumer = getDkifConsumer(env.urlEnv, azureAdTokenConsumer)
val accessControlService = AccessControlService(dkifConsumer)

val senderFacade = SenderFacade(
    dineSykmeldteHendelseKafkaProducer,
    dittSykefravaerMeldingKafkaProducer,
    brukernotifikasjonerService,
    arbeidsgiverNotifikasjonService,
    fysiskBrevUtsendingService,
    database,
)

val varselBusService = VarselBusService(...all services...)
```

When adding new dependencies, wire them in `BootstrapApplication.kt` following the existing pattern.

## Configuration Pattern

Configuration is loaded via nested data classes in `Environment.kt`:

```kotlin
data class Environment(
    val appEnv: AppEnv,        // port, threads, cluster
    val authEnv: AuthEnv,      // Azure/TokenX credentials
    val urlEnv: UrlEnv,        // 15+ external service URLs
    val kafkaEnv: KafkaEnv,    // bootstrap servers, SSL
    val dbEnv: DbEnv,          // GCP Cloud SQL connection
    val toggleEnv: ToggleEnv   // feature flags
)

// Helper functions control environment-specific behavior
fun isLocal(): Boolean = ...
fun isDevGcp(): Boolean = ...
```

Local dev uses `localEnvApp.json` / `localEnvJob.json`. Deployed environments use env vars from NAIS.

## Database Access

PostgreSQL with Flyway migrations in `src/main/resources/db/migration/`.

### DAO Extension Functions

Database queries use extension functions on `DatabaseInterface` (no ORM, no Kotliquery):

```kotlin
// ✅ Good - extension functions on DatabaseInterface with PreparedStatement
fun DatabaseInterface.storeUtsendtVarsel(varsel: PUtsendtVarsel) {
    val stmt = "INSERT INTO UTSENDT_VARSEL (uuid, fnr, type, kanal) VALUES (?, ?, ?, ?)"
    connection.prepareStatement(stmt).use { ps ->
        ps.setString(1, varsel.uuid)
        ps.setString(2, varsel.fnr)
        ps.setString(3, varsel.type)
        ps.setString(4, varsel.kanal)
        ps.executeUpdate()
    }.apply { commit() }
}

fun DatabaseInterface.fetchUferdigstilteVarsler(fnr: PersonIdent): List<PUtsendtVarsel> {
    val stmt = "SELECT * FROM UTSENDT_VARSEL WHERE fnr = ? AND ferdigstilt_tidspunkt IS NULL"
    return connection.prepareStatement(stmt).use { ps ->
        ps.setString(1, fnr.value)
        ps.executeQuery().toList { toPUtsendtVarsel() }
    }
}

// ❌ Bad - string concatenation
fun findByFnr(fnr: String) {
    val sql = "SELECT * FROM table WHERE fnr = '$fnr'"  // NEVER DO THIS
}
```

Key tables: `UTSENDT_VARSEL`, `PLANLAGT_VARSEL`, `ARBEIDSGIVERNOTIFIKASJON`, `MIKROFRONTEND`, `BIRTHDATE`.

## Service Pattern

Every varsel service follows the same structure:

```kotlin
class ExampleVarselService(
    private val accessControlService: AccessControlService,
    private val senderFacade: SenderFacade,
) {
    suspend fun processHendelse(hendelse: EsyfovarselHendelse) {
        // 1. Check feature toggle / preconditions
        if (!isEnabled()) return

        // 2. Check access control via DKIF
        val accessStatus = accessControlService.getUserAccessStatus(hendelse.fnr)
        if (!accessStatus.canBeNotified) {
            logger.warn { "User cannot be notified digitally" }
            return
        }

        // 3. Select notification text from templates (VarselTexts.kt)
        val text = VarselTexts.getText(hendelse.type)

        // 4. Delegate to SenderFacade for channel dispatch
        senderFacade.sendTilBrukernotifikasjoner(...)
    }
}
```

## Event Routing (VarselBusService)

Routes events by `HendelseType` using a `when` expression:

```kotlin
suspend fun processVarselHendelse(varselHendelse: EsyfovarselHendelse) {
    when (varselHendelse.type) {
        NL_OPPFOLGINGSPLAN_FORESPORSEL ->
            oppfolgingsplanVarselService.sendOppfolgingsplanForesporselVarselTilNarmesteLeder(...)
        SM_AKTIVITETSPLIKT ->
            aktivitetspliktForhandsvarselService.sendVarselTilArbeidstaker(...)
        // ... ~13 total handlers
    }
}
```

## Kafka Consumer

Manual offset commit, max poll records: 1 (process one message at a time).

```kotlin
// Custom JacksonKafkaSerializer with JavaTimeModule, ISO-8601 dates
// Consumer group: esyfovarsel-group-v04-gcp-v03
// Topic: team-esyfo.varselbus
```

Event flow:
```
Kafka (varselbus) → VarselBusKafkaConsumer → VarselBusService → [specific service] → SenderFacade → [channel]
```

## Kafka Producers

Output channel producers wrap Kafka's `KafkaProducer` directly:

```kotlin
class BrukernotifikasjonKafkaProducer(val env: Environment) {
    private val kafkaProducer = KafkaProducer<String, String>(producerProperties(env))

    fun sendBeskjed(fnr: String, content: String, uuid: String, varselUrl: URL?, ...) {
        val varsel = createVarsel(Varseltype.Beskjed, ...)
        kafkaProducer.send(ProducerRecord(topic, uuid, varsel)).get()
    }

    fun sendOppgave(...) { /* similar pattern */ }
    fun sendDone(uuid: String) { /* inaktiver variant */ }
}
```

## SenderFacade — Multi-Channel Dispatch

Central dispatcher supporting 5 output channels:

```kotlin
class SenderFacade(
    private val dineSykmeldteProducer: DineSykmeldteHendelseKafkaProducer,
    private val dittSykefravaerProducer: DittSykefravaerMeldingKafkaProducer,
    private val brukernotifikasjonerService: BrukernotifikasjonerService,
    private val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    private val fysiskBrevUtsendingService: FysiskBrevUtsendingService,
    private val database: DatabaseInterface,
) {
    fun sendTilDineSykmeldte(varselHendelse: NarmesteLederHendelse, varsel: DineSykmeldteVarsel)
    fun sendTilDittSykefravaer(varselHendelse: ArbeidstakerHendelse, varsel: DittSykefravaerVarsel)
    fun sendTilBrukernotifikasjoner(uuid: String, fnr: String, content: String, ...)
    suspend fun sendTilArbeidsgiverNotifikasjon(varselHendelse: ..., notifikasjon: ...)
    suspend fun sendBrevTilFysiskPrint(uuid: String, ...)

    // Completion/cleanup
    fun ferdigstillVarsel(uuid: String, kanal: VarselKanal)
}
```

Failed deliveries are tracked in `UtsendtVarselFeilet` table for error recovery.

## GraphQL (Apollo Kotlin)

Apollo Kotlin client for employer notifications (`arbeidsgivernotifikasjon`). Schema and mutations in `src/main/graphql/`. Generated code excluded from ktlint (`.editorconfig`).

## Ktor Routing

```kotlin
fun Application.api(applicationState: ApplicationState, meterRegistry: PrometheusMeterRegistry) {
    routing {
        // Health endpoints (unauthenticated)
        get("/isalive") { call.respondText("Alive") }
        get("/isready") { call.respondText("Ready") }
        get("/metrics") { call.respondText(meterRegistry.scrape()) }
    }
}
```

## Authentication

- **Local**: Basic auth only
- **Remote**: JWT (Azure AD + Token X) + Basic auth
- JwkProvider with caching + rate limiting (10/24hrs, 10/min)

```kotlin
// Authentication is configured in Authentication.kt
// Local dev uses basic auth, production uses JWT validation
```

## Observability

### Structured Logging

```kotlin
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

logger.info { "Processing event: ${event.type}" }
logger.warn { "Retrying failed operation" }
logger.error(exception) { "Failed to process event" }
```

### Prometheus Metrics

```kotlin
val meterRegistry = PrometheusMeterRegistry(
    PrometheusConfig.DEFAULT,
    CollectorRegistry.defaultRegistry,
    Clock.SYSTEM
)

// Counter example
val eventsProcessed = Counter.builder("events_processed_total")
    .description("Total events processed")
    .tag("event_type", "varsel")
    .register(meterRegistry)

eventsProcessed.increment()

// Timer example
val processingDuration = Timer.builder("event_processing_duration_seconds")
    .description("Event processing duration")
    .register(meterRegistry)

processingDuration.record { processEvent() }
```

## Coroutines

Many services use Kotlin coroutines (`suspend fun`, `launch`, `coroutineScope`):

```kotlin
// Suspend functions for async operations (HTTP calls, GraphQL)
suspend fun sendTilArbeidsgiverNotifikasjon(hendelse: ..., notifikasjon: ...) {
    // Apollo GraphQL client calls are suspend functions
    val response = apolloClient.mutation(CreateNotifikasjonMutation(...)).execute()
}

// Launched within coroutine scope for parallel operations
launch {
    varselBusService.processVarselHendelse(hendelse)
}
```

## Boundaries

### ✅ Always

- Use constructor injection for dependencies (wire in BootstrapApplication.kt)
- Follow the service pattern (preconditions → access → text → SenderFacade)
- Use Flyway for database migrations
- Use extension functions on `DatabaseInterface` for queries
- Use parameterized queries (PreparedStatement) — never string concatenation
- Implement all three health endpoints (`/isalive`, `/isready`, `/metrics`)
- Add Prometheus metrics for business operations
- Use structured logging with KotlinLogging

### ⚠️ Ask First

- Changing database schema (requires Flyway migration)
- Modifying Kafka consumer configuration or consumer group
- Adding new HendelseType routing in VarselBusService
- Changing authentication configuration
- Adding new output channels to SenderFacade
- Modifying NAIS manifests

### 🚫 Never

- Skip database migration versioning
- Bypass authentication or access control checks
- Use `!!` operator without proper null checks
- Commit configuration secrets
- Log personal data (fødselsnummer, tokens)
- Use string concatenation in SQL queries
