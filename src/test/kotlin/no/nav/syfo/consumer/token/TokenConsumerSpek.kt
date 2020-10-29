package no.nav.syfo.consumer.token

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.fullPath
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ServiceUserSecrets
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TokenConsumerSpek: Spek({
    val mockClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when (request.url.fullPath) {
                    "/rest/v1/sts/token" -> {
                        respond("""{"access_token": "TOKEN", "token_type": "Bearer", "expires_in": 3600}""")
                    }
                    else -> error("Endepunktet finnes ikke ${request.url.fullPath}")
                }
            }
        }
    }

    describe("TokenConsumerSpek") {
        it("skal hente token fra STS") {
            val tokenConsumer = TokenConsumer(ServiceUserSecrets("username", "password"), mockClient)
            runBlocking {
                val token = tokenConsumer.token()
                "TOKEN" shouldEqual token
            }
        }
    }
})
