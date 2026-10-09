package no.nav.syfo.consumer.narmesteLeder

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.auth.ITokenConsumer
import no.nav.syfo.getTestEnv
import no.nav.syfo.testutil.mocks.MockServers
import no.nav.syfo.testutil.mocks.NarmesteLederMockServer
import org.slf4j.LoggerFactory

class NarmesteLederConsumerSpek :
    DescribeSpec({
        // Synthetic identifiers and addresses, never operational data.
        val employeeFnr = "01010112345"
        val leaderFnr = "02020254321"
        val orgnummer = "999888777"
        val leaderId = "c2e7a159-7d55-4a3a-b5e7-c118bb87fa59"
        val email = "a@example.invalid"
        val secondEmail = "b@example.invalid"
        val mapper = jacksonObjectMapper()
        val testEnv = getTestEnv()
        val mockServers = MockServers(testEnv.urlEnv, testEnv.authEnv)
        val aadServer = mockServers.mockAADServer()
        val tokenConsumer = AzureAdTokenConsumer(testEnv.authEnv)
        val consumer = NarmesteLederConsumer(testEnv.urlEnv, tokenConsumer)
        val logger = LoggerFactory.getLogger(NarmesteLederConsumer::class.qualifiedName) as Logger
        val appender = ListAppender<ILoggingEvent>()
        lateinit var leaderMock: NarmesteLederMockServer

        fun response(addresses: List<String>): String =
            mapper.writeValueAsString(
                mapOf(
                    "lineManager" to
                        mapOf(
                            "id" to leaderId,
                            "nationalIdentificationNumber" to leaderFnr,
                            "emailAddresses" to addresses,
                        ),
                ),
            )

        beforeSpec {
            testEnv.urlEnv.narmestelederUrl shouldBe "http://localhost:9097"
            appender.start()
            logger.addAppender(appender)
            aadServer.start()
        }

        beforeEach {
            appender.list.clear()
            leaderMock = NarmesteLederMockServer(mockServers)
            leaderMock.server.start()
        }

        afterEach {
            leaderMock.server.stop(1L, 10L)
            val forbiddenValues = listOf(employeeFnr, leaderFnr, orgnummer, email, secondEmail)
            appender.list.forEach { event ->
                val loggedValues =
                    listOfNotNull(event.formattedMessage) +
                        event.argumentArray.orEmpty().map { it.toString() } +
                        event.keyValuePairs.orEmpty().map { "${it.key}=${it.value}" }
                loggedValues.any { value -> forbiddenValues.any { value.contains(it) } } shouldBe false
                event.throwableProxy shouldBe null
            }
        }

        afterSpec {
            logger.detachAppender(appender)
            appender.stop()
            aadServer.stop(1L, 10L)
            tokenConsumer.httpClientWithProxy.close()
        }

        describe("Nearest-leader lookup") {
            it("Posts identifiers only in the JSON body and maps a single email address") {
                leaderMock.responseBody = response(listOf(email))

                consumer.getNarmesteLeder(employeeFnr, orgnummer) shouldBe
                    NarmesteLederRelasjon(leaderId, leaderFnr, email)

                val request = requireNotNull(leaderMock.request)
                request.method shouldBe "POST"
                request.uri shouldBe "/internal/api/v1/lookup"
                mapper.readTree(request.body) shouldBe
                    mapper.valueToTree(
                        mapOf(
                            "employeeNationalIdentificationNumber" to employeeFnr,
                            "organizationNumber" to orgnummer,
                        ),
                    )
                request.headers.entries
                    .first { it.key.equals("Authorization", ignoreCase = true) }
                    .value
                    .single()
                    .let { it.startsWith("Bearer ") && it.length > 7 } shouldBe true
                request.uri.contains(employeeFnr) shouldBe false
                request.headers.values
                    .flatten()
                    .any { it.contains(employeeFnr) } shouldBe false
            }

            it("Joins multiple email addresses with semicolons") {
                leaderMock.responseBody = response(listOf(email, secondEmail))
                consumer.getNarmesteLeder(employeeFnr, orgnummer)?.narmesteLederEpost shouldBe "$email;$secondEmail"
            }

            it("Maps an empty email list to a leader without email") {
                leaderMock.responseBody = response(emptyList())
                consumer.getNarmesteLeder(employeeFnr, orgnummer) shouldBe
                    NarmesteLederRelasjon(leaderId, leaderFnr, null)
            }

            it("Returns null when there is no line manager") {
                consumer.getNarmesteLeder(employeeFnr, orgnummer) shouldBe null
            }

            listOf(400, 401, 403, 500).forEach { status ->
                it("Returns null and logs only the status for HTTP $status") {
                    leaderMock.status = HttpStatusCode.fromValue(status)
                    leaderMock.responseBody = response(listOf(email))
                    consumer.getNarmesteLeder(employeeFnr, orgnummer) shouldBe null
                    appender.list
                        .single()
                        .keyValuePairs
                        .single()
                        .let { it.key to it.value } shouldBe
                        ("status" to status)
                }
            }

            it("Returns null for malformed JSON without logging its contents") {
                leaderMock.responseBody = response(listOf(email)).dropLast(1)
                consumer.getNarmesteLeder(employeeFnr, orgnummer) shouldBe null
                appender.list
                    .single()
                    .keyValuePairs
                    .single()
                    .key shouldBe "exception"
            }

            it("Returns null for a structurally invalid response") {
                leaderMock.responseBody = """{"lineManager":{"nationalIdentificationNumber":"$leaderFnr"}}"""
                consumer.getNarmesteLeder(employeeFnr, orgnummer) shouldBe null
            }

            it("Returns null when the server is unreachable") {
                leaderMock.server.stop(1L, 10L)
                consumer.getNarmesteLeder(employeeFnr, orgnummer) shouldBe null
                appender.list
                    .single()
                    .keyValuePairs
                    .single()
                    .key shouldBe "exception"
            }

            it("Returns null when token acquisition fails without logging the exception message") {
                val tokens = mockk<ITokenConsumer>()
                coEvery { tokens.getToken(any()) } throws IllegalStateException("$employeeFnr $leaderFnr $email")
                NarmesteLederConsumer(testEnv.urlEnv, tokens).getNarmesteLeder(employeeFnr, orgnummer) shouldBe null
                appender.list
                    .single()
                    .keyValuePairs
                    .single()
                    .let { it.key to it.value } shouldBe
                    ("exception" to IllegalStateException::class.java.name)
            }

            it("Rethrows cancellation") {
                val tokens = mockk<ITokenConsumer>()
                val cancellation = CancellationException("$employeeFnr $email")
                coEvery { tokens.getToken(any()) } throws cancellation
                var caught: CancellationException? = null
                try {
                    NarmesteLederConsumer(testEnv.urlEnv, tokens).getNarmesteLeder(employeeFnr, orgnummer)
                } catch (e: CancellationException) {
                    caught = e
                }
                (caught === cancellation) shouldBe true
            }

            it("Rethrows Error without logging its message or throwable") {
                val tokens = mockk<ITokenConsumer>()
                val error = AssertionError("$employeeFnr $leaderFnr $email")
                coEvery { tokens.getToken(any()) } throws error
                var caught: Error? = null
                try {
                    NarmesteLederConsumer(testEnv.urlEnv, tokens).getNarmesteLeder(employeeFnr, orgnummer)
                } catch (e: Error) {
                    caught = e
                }
                (caught === error) shouldBe true
                appender.list
                    .single()
                    .keyValuePairs
                    .single()
                    .let { it.key to it.value } shouldBe
                    ("exception" to AssertionError::class.java.name)
            }
        }
    })
