---
applyTo: "**/*Spek.kt,**/*Test.kt,**/*Tests.kt"
---

# Testing Standards (Kotlin)

## Test Framework

- **Framework**: Kotest `DescribeSpec` with `describe { }` / `it { }` blocks
- **Mocking**: MockK (`mockk(relaxed = true)`, `coEvery`/`coVerify` for coroutines)
- **Database**: Testcontainers PostgreSQL via `EmbeddedDatabase` singleton (shared across tests, auto-migrated with Flyway)
- **Assertions**: Mix of Kotest assertions and Kluent (`shouldBeEqualTo`)
- **Test files**: Named `*Spek.kt` or `*Test.kt`

## Test Structure (DescribeSpec)

```kotlin
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import io.mockk.coEvery
import io.mockk.coVerify
import org.amshove.kluent.shouldBeEqualTo

class SenderFacadeSpek : DescribeSpec({

    describe("SenderFacade") {
        val mockProducer = mockk<BrukernotifikasjonKafkaProducer>(relaxed = true)
        val mockDatabase = mockk<DatabaseInterface>(relaxed = true)
        val senderFacade = SenderFacade(mockProducer, ..., mockDatabase)

        beforeTest {
            // Reset state before each test if needed
        }

        it("should send varsel to brukernotifikasjoner") {
            // Arrange
            val varsel = createTestVarsel()

            // Act
            senderFacade.sendTilBrukernotifikasjoner(varsel)

            // Assert
            verify(exactly = 1) { mockProducer.sendBeskjed(any(), any(), any(), any()) }
        }

        it("should not send when preconditions are not met") {
            val varsel = createInvalidVarsel()

            senderFacade.sendTilBrukernotifikasjoner(varsel)

            verify(exactly = 0) { mockProducer.sendBeskjed(any(), any(), any(), any()) }
        }

        it("should track failed deliveries") {
            every { mockProducer.sendBeskjed(any(), any(), any(), any()) } throws RuntimeException("Kafka error")

            senderFacade.sendTilBrukernotifikasjoner(createTestVarsel())

            verify { mockDatabase.storeUtsendtVarselFeilet(any()) }
        }
    }
})
```

### Nested Describes

```kotlin
class VarselBusServiceSpek : DescribeSpec({

    describe("VarselBusService") {

        describe("when handling NL_OPPFOLGINGSPLAN_FORESPORSEL") {
            it("should delegate to oppfolgingsplanVarselService") {
                // ...
            }

            it("should skip when feature toggle is disabled") {
                // ...
            }
        }

        describe("when handling SM_AKTIVITETSPLIKT") {
            it("should check access control before sending") {
                // ...
            }
        }
    }
})
```

## Assertions

```kotlin
// Kluent (preferred in this project)
result shouldBeEqualTo expected
result.size shouldBeEqualTo 3
list shouldContain item

// Kotest
result shouldBe expected
result shouldNotBe null
list shouldContain item
list shouldContainAll listOf(item1, item2)
list shouldHaveSize 3

// Null checks
result shouldNotBe null
result!!.name shouldBeEqualTo "expected"
nullableValue shouldBe null

// Numeric comparisons
value shouldBeGreaterThan 0
value shouldBeLessThanOrEqual 100

// Exceptions
shouldThrow<IllegalArgumentException> {
    service.processInvalid()
}
```

## Mocking with MockK

```kotlin
// Relaxed mock (returns default values for all calls)
val mockService = mockk<SomeService>(relaxed = true)

// Specific behavior
every { mockService.findById(any()) } returns testEntity
every { mockService.findById("unknown") } returns null

// Coroutine mocking (suspend functions)
coEvery { mockService.fetchAsync(any()) } returns result
coEvery { mockService.fetchAsync("error") } throws RuntimeException("Failed")

// Verification
verify(exactly = 1) { mockService.save(any()) }
verify(exactly = 0) { mockService.delete(any()) }
coVerify { mockService.fetchAsync("test-id") }

// Argument capture
val slot = slot<PUtsendtVarsel>()
every { mockDatabase.storeUtsendtVarsel(capture(slot)) } just Runs
// After calling:
slot.captured.fnr shouldBeEqualTo "12345678901"

// Verify order
verifyOrder {
    mockService.checkAccess(any())
    mockService.sendVarsel(any())
}
```

## Database Tests (Testcontainers)

```kotlin
class UtsendtVarselDAOSpek : DescribeSpec({

    describe("UtsendtVarselDAO") {
        val embeddedDatabase = EmbeddedDatabase()  // Shared singleton, auto-migrated with Flyway

        beforeTest {
            // Clean relevant tables before each test
            embeddedDatabase.connection.prepareStatement("DELETE FROM UTSENDT_VARSEL").execute()
        }

        it("should save and retrieve utsendt varsel") {
            embeddedDatabase.storeUtsendtVarsel(testVarsel)

            val result = embeddedDatabase.fetchUferdigstilteVarsler(PersonIdent(testVarsel.fnr))

            result shouldHaveSize 1
            result.first().uuid shouldBeEqualTo testVarsel.uuid
            result.first().type shouldBeEqualTo testVarsel.type
        }

        it("should not return ferdigstilte varsler") {
            embeddedDatabase.storeUtsendtVarsel(testVarsel)
            embeddedDatabase.ferdigstillVarsel(testVarsel.uuid)

            val result = embeddedDatabase.fetchUferdigstilteVarsler(PersonIdent(testVarsel.fnr))

            result shouldHaveSize 0
        }
    }
})
```

### EmbeddedDatabase Singleton

```kotlin
// Shared across all tests — auto-migrated on first access
val database = EmbeddedDatabase()

// Usage in test
database.storeUtsendtVarsel(varsel)
val results = database.fetchUferdigstilteVarsler(fnr)
```

## Testing Services with Access Control

```kotlin
class ExampleVarselServiceSpek : DescribeSpec({

    describe("ExampleVarselService") {
        val mockAccessControlService = mockk<AccessControlService>()
        val mockSenderFacade = mockk<SenderFacade>(relaxed = true)
        val service = ExampleVarselService(mockAccessControlService, mockSenderFacade)

        it("should send varsel when user can be notified") {
            coEvery { mockAccessControlService.getUserAccessStatus(any()) } returns
                UserAccessStatus(fnr = "12345678901", canBeNotified = true)

            service.processHendelse(createTestHendelse())

            verify(exactly = 1) { mockSenderFacade.sendTilBrukernotifikasjoner(any(), any(), any()) }
        }

        it("should not send varsel when user cannot be notified digitally") {
            coEvery { mockAccessControlService.getUserAccessStatus(any()) } returns
                UserAccessStatus(fnr = "12345678901", canBeNotified = false)

            service.processHendelse(createTestHendelse())

            verify(exactly = 0) { mockSenderFacade.sendTilBrukernotifikasjoner(any(), any(), any()) }
        }
    }
})
```

## Testing Authentication (MockOAuth2Server)

```kotlin
import no.nav.security.mock.oauth2.MockOAuth2Server

class AuthenticationSpek : DescribeSpec({

    describe("Authentication") {
        val mockOAuth2Server = MockOAuth2Server()

        beforeSpec {
            mockOAuth2Server.start()
        }

        afterSpec {
            mockOAuth2Server.shutdown()
        }

        it("should authenticate with valid Azure AD token") {
            val token = mockOAuth2Server.issueToken(
                issuerId = "azuread",
                subject = "test-user",
                claims = mapOf("preferred_username" to "test@nav.no")
            )

            val response = client.get("/api/protected") {
                bearerAuth(token.serialize())
            }

            response.status shouldBe HttpStatusCode.OK
        }

        it("should reject request without token") {
            val response = client.get("/api/protected")

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
```

## Testing Coroutines

```kotlin
class AsyncServiceSpek : DescribeSpec({

    describe("AsyncService") {
        val mockClient = mockk<ArbeidsgiverNotifikasjonProdusent>(relaxed = true)

        it("should handle concurrent varsel sending") {
            coEvery { mockClient.sendNotifikasjon(any()) } returns OpprettNyBeskjedResultat.Success

            // DescribeSpec automatically handles coroutines
            service.sendTilArbeidsgiverNotifikasjon(testHendelse, testNotifikasjon)

            coVerify(exactly = 1) { mockClient.sendNotifikasjon(any()) }
        }

        it("should handle async failures gracefully") {
            coEvery { mockClient.sendNotifikasjon(any()) } throws RuntimeException("GraphQL error")

            shouldThrow<RuntimeException> {
                service.sendTilArbeidsgiverNotifikasjon(testHendelse, testNotifikasjon)
            }
        }
    }
})
```

## Test Data Helpers

```kotlin
// Create test data using helper functions
fun createTestHendelse(
    type: HendelseType = HendelseType.SM_AKTIVITETSPLIKT,
    fnr: String = "12345678901",
) = EsyfovarselHendelse(
    type = type,
    ferdigstill = false,
    data = null,
    arbeidstakerFnr = fnr,
)

fun createTestVarsel(
    uuid: String = UUID.randomUUID().toString(),
    fnr: String = "12345678901",
) = PUtsendtVarsel(
    uuid = uuid,
    fnr = fnr,
    type = "SM_AKTIVITETSPLIKT",
    kanal = "BRUKERNOTIFIKASJON",
)
```

## Running Tests

```bash
# All tests
./gradlew test

# Single test class
./gradlew test --tests "no.nav.syfo.service.SenderFacadeSpek"

# Single test by description (Kotest)
./gradlew test --tests "no.nav.syfo.service.SenderFacadeSpek" -Dkotest.filter.specs="*SenderFacadeSpek"

# Build + test + lint (recommended before commit)
./gradlew build
```

## Test Coverage Requirements

- **Services**: Test both success and error paths, access control variations
- **DAOs**: Test CRUD operations, edge cases (empty results, duplicates)
- **Kafka producers**: Verify correct topic and message format
- **SenderFacade**: Test each channel dispatch + failure tracking
- **Event routing**: Test each HendelseType dispatches to correct service

## Test Naming

```kotlin
// ✅ Good - descriptive behavior
it("should send varsel when access is granted") { ... }
it("should not send when bruker has no active sykmelding") { ... }
it("should persist utsendt varsel to database") { ... }
it("should track failed delivery in UtsendtVarselFeilet") { ... }

// ❌ Bad - not descriptive
it("test1") { ... }
it("sendVarselTest") { ... }
it("works") { ... }
```

## Boundaries

### ✅ Always

- Write tests for new code before committing
- Test both success and error cases
- Test access control variations (canBeNotified true/false)
- Use descriptive test names in `it { }` blocks
- Clean up test data in `beforeTest` blocks
- Run full test suite before pushing (`./gradlew test`)
- Use `mockk(relaxed = true)` for mocks unless specific behavior needed
- Use `coEvery`/`coVerify` for suspend functions

### ⚠️ Ask First

- Changing test framework or structure
- Adding complex test fixtures
- Modifying shared test utilities (EmbeddedDatabase)
- Disabling or skipping tests

### 🚫 Never

- Commit failing tests
- Skip tests without good reason
- Test implementation details (test behavior, not internals)
- Share mutable state between tests
- Commit without running tests
- Use real fødselsnummer in test data
