# Copilot Instructions for esyfovarsel

---

# Nav Development Standards

These standards apply across Nav projects. Project-specific guidelines follow below.

## Nav Principles

- **Team First**: Autonomous teams with circles of autonomy, supported by Architecture Advice Process
- **Product Development**: Continuous development and product-organized reuse over ad hoc approaches
- **Essential Complexity**: Focus on essential complexity, avoid accidental complexity
- **DORA Metrics**: Measure and improve team performance using DevOps Research and Assessment metrics

## Nav Tech Stack

- **Backend**: Kotlin with Ktor, PostgreSQL, Apache Kafka
- **Platform**: Nais (Kubernetes on Google Cloud Platform)
- **Auth**: Azure AD, TokenX, ID-porten, Maskinporten
- **Observability**: Prometheus, Grafana Loki, Tempo (OpenTelemetry)

## Nav Code Standards

### Kotlin/Ktor Patterns

- Manual dependency wiring (no DI framework) — constructor injection
- Extension functions on `DatabaseInterface` for database queries (no ORM)
- Flyway for database migrations
- Kotest DescribeSpec for testing, MockK for mocking, Kluent for assertions
- Coroutine-based async processing (`suspend fun`, `launch`, `coroutineScope`)

### Nais Deployment

- Manifests in `nais/` directory
- Required endpoints: `/isalive`, `/isready`, `/metrics`
- OpenTelemetry auto-instrumentation for observability
- Separate dev/prod manifests

---

# Application-Specific Guidelines

## What This Is

A Kotlin/Ktor notification orchestration service for NAV's sickness follow-up system (eSYFO). It consumes events from the Kafka topic `team-esyfo.varselbus` and dispatches notifications to multiple channels: DineSykmeldte, Brukernotifikasjoner (min-side), DittSykefravaer, Arbeidsgivernotifikasjoner (via GraphQL), and physical letters.

## Commands

**Run after all changes:** `./gradlew build`

```bash
./gradlew build              # Build + test + ktlint check
./gradlew test               # Tests only
./gradlew ktlintCheck        # Lint only
./gradlew ktlintFormat       # Auto-fix lint issues

# Run a single test class:
./gradlew test --tests "no.nav.syfo.service.SenderFacadeSpek"

# Run a single test by description (Kotest):
./gradlew test --tests "no.nav.syfo.service.SenderFacadeSpek" -Dkotest.filter.specs="*SenderFacadeSpek"
```

Java 21 runtime, Java 19 compilation target. Uses Shadow plugin for fat JAR (`no.nav.syfo.BootstrapApplicationKt` is the main class).

### Local Development

```bash
docker-compose up   # Starts Postgres 17, Kafka (Confluent), Zookeeper, Schema Registry
```

Then run `BootstrapApplication` from IDE. For job mode, run a second instance with env var `JOB=true`.

## Project Structure

```
src/main/kotlin/no/nav/syfo/
├── BootstrapApplication.kt     # Entry point, manual dependency wiring
├── Environment.kt              # Config from env vars / local JSON
├── ApplicationState.kt         # Liveness/readiness state
├── auth/                       # Azure AD, TokenX JWT validation
├── api/                        # Ktor routes (health, metrics)
├── service/                    # Business logic
│   ├── VarselBusService.kt     # Event router (HendelseType → service)
│   ├── SenderFacade.kt         # Multi-channel dispatcher
│   ├── AccessControlService.kt # DKIF contact info check
│   ├── *Service.kt             # ~13 specialized varsel services
│   └── VarselTexts.kt          # Notification text templates
├── consumer/                   # Kafka consumers (varselbus)
├── producer/                   # Kafka producers (outbound channels)
├── kafka/                      # Kafka config, serializers
├── db/                         # Database DAOs (extension functions)
├── job/                        # Scheduled jobs
└── utils/                      # Shared utilities

src/main/resources/
├── db/migration/               # Flyway SQL migrations (V1__*.sql)
├── localEnvApp.json            # Local dev config (app mode)
└── localEnvJob.json            # Local dev config (job mode)

src/main/graphql/               # Apollo Kotlin schema + mutations
nais/                           # NAIS deployment manifests (dev/prod)
```

## Architecture

### Event Flow

```
Kafka (varselbus topic) → VarselBusKafkaConsumer → VarselBusService → [specific service] → SenderFacade → [channel producer/service]
```

- **VarselBusService** (`service/VarselBusService.kt`): Routes `EsyfovarselHendelse` events by `HendelseType` to ~13 specialized services via `when` expression.
- **SenderFacade** (`service/SenderFacade.kt`): Central dispatcher that handles sending to all output channels and persisting sent/unsent status. Tracks failed deliveries in `UtsendtVarselFeilet` table.
- **Specialized services** (e.g., `AktivitetspliktForhandsvarselService`, `DialogmoteService`): Each handles one notification domain — checks preconditions/access, selects text templates, delegates to SenderFacade.

### Output Channels

| Channel | Implementation |
|---------|---------------|
| Brukernotifikasjoner (min-side) | `BrukernotifikasjonKafkaProducer` → `min-side.aapen-brukervarsel-v1` |
| DineSykmeldte | `DineSykmeldteHendelseKafkaProducer` → Kafka topic |
| DittSykefravaer | `DittSykefravaerMeldingKafkaProducer` → Kafka topic |
| Arbeidsgivernotifikasjoner | `ArbeidsgiverNotifikasjonProdusent` → GraphQL mutations (Apollo) |
| Fysisk brev | `FysiskBrevUtsendingService` → via journalpost |

### Database

PostgreSQL with Flyway migrations in `src/main/resources/db/migration/`.

Key tables:
- `UTSENDT_VARSEL` — Sent notifications (uuid, fnr, type, kanal, timestamps)
- `PLANLAGT_VARSEL` — Planned/scheduled notifications
- `ARBEIDSGIVERNOTIFIKASJON` — Employer notification tracking
- `MIKROFRONTEND` — Microfrontend toggle state
- `BIRTHDATE` — Cached birth dates

### Dependencies Wired in BootstrapApplication

No DI framework — all dependencies are manually constructed in `BootstrapApplication.kt` and passed through constructors:

```kotlin
// Typical wiring flow
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

## Key Conventions

### Service Pattern

Every varsel service follows the same structure:
1. Receive event data
2. Check feature toggle / preconditions
3. Check access control (`AccessControlService` → DKIF `kanVarsles`)
4. Select notification text from localized templates (`VarselTexts.kt`)
5. Delegate to `SenderFacade` for channel dispatch

### Kafka

- Manual offset commit (auto-commit disabled)
- Max poll records: 1 (process one message at a time)
- Custom `JacksonKafkaSerializer` with JavaTimeModule, ISO-8601 dates
- Consumer group: `esyfovarsel-group-v04-gcp-v03`
- Topic: `team-esyfo.varselbus`

### Database DAO Pattern

Database queries use extension functions on `DatabaseInterface` (no ORM):

```kotlin
fun DatabaseInterface.storeUtsendtVarsel(varsel: PUtsendtVarsel) {
    val stmt = "INSERT INTO UTSENDT_VARSEL (...) VALUES (?, ?, ...)"
    connection.prepareStatement(stmt).use { ... }.apply { commit() }
}

fun DatabaseInterface.fetchUferdigstilteVarsler(fnr: PersonIdent): List<PUtsendtVarsel> {
    val stmt = "SELECT * FROM UTSENDT_VARSEL WHERE fnr = ? AND ferdigstilt_tidspunkt IS NULL"
    return connection.prepareStatement(stmt).executeQuery().toList { toPUtsendtVarsel() }
}
```

### Testing

- **Framework**: Kotest `DescribeSpec` with `describe { }` / `it { }` blocks
- **Mocking**: MockK (`mockk(relaxed = true)`, `coEvery`/`coVerify` for coroutines)
- **Database**: Testcontainers PostgreSQL via `EmbeddedDatabase` singleton (shared across tests, auto-migrated with Flyway)
- **Assertions**: Mix of Kotest assertions and Kluent (`shouldBeEqualTo`)
- **Test files**: Named `*Spek.kt` or `*Test.kt`

### GraphQL

Apollo Kotlin client for employer notifications (`arbeidsgivernotifikasjon`). Schema and mutations live in `src/main/graphql/`. Generated code is excluded from ktlint (`.editorconfig`).

### Environment Configuration

`Environment.kt` loads config from local JSON files (`localEnvApp.json`, `localEnvJob.json`) or environment variables.

```kotlin
data class Environment(
    val appEnv: AppEnv,        // port, threads, cluster
    val authEnv: AuthEnv,      // Azure/TokenX credentials
    val urlEnv: UrlEnv,        // 15+ external service URLs
    val kafkaEnv: KafkaEnv,    // bootstrap servers, SSL
    val dbEnv: DbEnv,          // GCP Cloud SQL connection
    val toggleEnv: ToggleEnv   // feature flags
)
```

Helper functions `isLocal()` and `isDevGcp()` control environment-specific behavior.

### Authentication

- **Local**: Basic auth only
- **Remote**: JWT (Azure AD + Token X) + Basic auth
- JwkProvider with caching + rate limiting (10/24hrs, 10/min)

### Deployment

NAIS platform (Kubernetes). Config in `nais/` directory with separate dev/prod manifests. CI via shared GitHub Actions workflow (`navikt/teamesyfo-github-actions-workflows`).

## Documentation

Keep temporary working notes in `.local-notes/`, which is ignored by Git.
Maintain durable service documentation in `README.md` and the existing `docs/` layout as
part of the authorized change. Record an ADR for a lasting architectural
tradeoff or a change to an earlier architectural decision, following existing
ADR paths and numbering when present. The task scope determines which docs
need updating; ask only when a material decision or authority is missing.

## Repository guidance

This repository owns `.github/copilot-instructions.md`, applicable files under
`.github/instructions/`, and retained local agents and skills. Update affected
repository guidance together with an authorized change, preserving service
facts, build commands, data rules, and operational constraints.

Portable agents and task workflows come from the selected nav-pilot package.
Use the exact component identities offered by the active session. Check local
and user components for name collisions when a skill is missing or resolves to
unexpected content.

## Boundaries

### ✅ Always

- Run `./gradlew build` after changes to verify build + tests + lint
- Use parameterized queries for all database access
- Follow existing service pattern (event → preconditions → access control → text → SenderFacade)
- Write Kotest DescribeSpec tests for new functionality
- Use Flyway migrations for schema changes (never modify existing migrations)
- Include structured logging with relevant context (event type, varsel type)
- Use constructor injection for all dependencies
- Handle errors gracefully — failed deliveries tracked in `UtsendtVarselFeilet`

### ⚠️ Ask First

- Modifying Kafka consumer configuration or consumer group
- Changing access control logic (AccessControlService)
- Adding new HendelseType routing in VarselBusService
- Modifying production NAIS manifests
- Changing GraphQL schema or mutations
- Adding new output channels to SenderFacade

### 🚫 Never

- Commit secrets or credentials to git
- Skip input validation
- Log personal data (fødselsnummer, tokens)
- Bypass authentication or access control checks
- Modify existing Flyway migration files
- Use string concatenation in SQL queries
- Use `!!` operator without proper null checks
