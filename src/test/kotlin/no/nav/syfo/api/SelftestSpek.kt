package no.nav.syfo.api

import io.kotest.core.spec.style.DescribeSpec
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.syfo.ApplicationState
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo

class SelftestSpek : DescribeSpec({

    val applicationState = ApplicationState()

    describe("Calling selftest with successful liveness and readyness tests") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerNaisApi(applicationState)
            }

            it("Returns OK on isAlive") {
                applicationState.running = true

                with(handleRequest(HttpMethod.Get, "/isAlive")) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
            it("Returns OK on isReady") {
                applicationState.initialized = true

                with(handleRequest(HttpMethod.Get, "/isReady")) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
            it("Returns error on failed isAlive") {
                applicationState.running = false

                with(handleRequest(HttpMethod.Get, "/isAlive")) {
                    response.status()?.isSuccess() shouldNotBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
            it("Returns error on failed isReady") {
                applicationState.initialized = false

                with(handleRequest(HttpMethod.Get, "/isReady")) {
                    response.status()?.isSuccess() shouldNotBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }

    describe("Calling selftests with unsuccessful liveness test") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerNaisApi(ApplicationState(running = false))
            }

            it("Returns internal server error when liveness check fails") {
                with(handleRequest(HttpMethod.Get, "/isAlive")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }

    describe("Calling selftests with unsucessful readyness test") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerNaisApi(ApplicationState(initialized = false))
            }

            it("Returns internal server error when readyness check fails") {
                with(handleRequest(HttpMethod.Get, "/isReady")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }
})
