package no.nav.syfo.testutil.mocks

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post

class NarmesteLederMockServer(
    mockServers: MockServers,
) {
    @Volatile
    var status = HttpStatusCode.OK

    @Volatile
    var responseBody = """{"lineManager":null}"""

    @Volatile
    var request: LookupRequest? = null

    val server =
        mockServers.mockServer(mockServers.urlEnv.narmestelederUrl) {
            post("/internal/api/v1/lookup") {
                request =
                    LookupRequest(
                        method = call.request.local.method.value,
                        uri = call.request.local.uri,
                        headers =
                            call.request.headers
                                .entries()
                                .associate { it.key to it.value },
                        body = call.receiveText(),
                    )
                call.respondText(responseBody, ContentType.Application.Json, status)
            }
        }
}

data class LookupRequest(
    val method: String,
    val uri: String,
    val headers: Map<String, List<String>>,
    val body: String,
)
