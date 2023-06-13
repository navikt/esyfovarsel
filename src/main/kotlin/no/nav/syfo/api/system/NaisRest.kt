package no.nav.syfo.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import no.nav.syfo.ApplicationState

fun Routing.registerNaisApi(
    applicationState: ApplicationState,
) {
    get("/isAlive") {
        if (applicationState.running) {
            call.respondText("Application is alive")
        } else {
            call.respondText("Application is dead", status = HttpStatusCode.InternalServerError)
        }
    }

    get("/isReady") {
        if (applicationState.initialized) {
            call.respondText("Application is ready")
        } else {
            call.respondText("Application is not ready", status = HttpStatusCode.InternalServerError)
        }
    }
}
