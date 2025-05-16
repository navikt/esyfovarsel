package no.nav.syfo.api

import io.kotest.core.spec.style.DescribeSpec
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.syfo.ApplicationState
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo

class SelftestSpek : DescribeSpec({

    describe("Calling selftest with successful liveness and readyness tests") {
        it("Returns OK on isAlive") {
            withTestApplicationState(ApplicationState(running = true)) {
                val response = client.get("/isAlive")
                response.status.isSuccess() shouldBeEqualTo true
                response.body<Any>() shouldNotBeEqualTo null
            }
        }
        it("Returns OK on isReady") {
            withTestApplicationState(ApplicationState(initialized = true)) {
                val response = client.get("/isReady")
                response.status.isSuccess() shouldBeEqualTo true
                response.body<Any>() shouldNotBeEqualTo null
            }
        }
        it("Returns error on failed isAlive") {
            withTestApplicationState(ApplicationState(running = false)) {

                val response = client.get("/isAlive")
                response.status.isSuccess() shouldNotBeEqualTo true
                response.body<Any>() shouldNotBeEqualTo null
            }
        }
        it("Returns error on failed isReady") {
            withTestApplicationState(ApplicationState(initialized = false)) {

                val response = client.get("/isReady")
                response.status.isSuccess() shouldNotBeEqualTo true
                response.body<Any>() shouldNotBeEqualTo null
            }
        }
    }

    describe("Calling selftests with unsuccessful liveness test") {
        it("Returns internal server error when liveness check fails") {
            withTestApplicationState(ApplicationState(initialized = false)) {
                val response = client.get("/isAlive")
                response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                response.body<Any>() shouldNotBeEqualTo null
            }
        }
    }

    describe("Calling selftests with unsucessful readyness test") {
        it("Returns internal server error when liveness check fails") {
            withTestApplicationState(ApplicationState(running = false)) {
                val response = client.get("/isReady")
                response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                response.body<Any>() shouldNotBeEqualTo null
            }
        }
    }
})

private fun withTestApplicationState(
    applicationState: ApplicationState,
    fn: suspend ApplicationTestBuilder.() -> Unit
) {
    testApplication {
        application {
            routing {
                registerNaisApi(applicationState)
            }
        }
        fn(this)
    }
}
