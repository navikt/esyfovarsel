package no.nav.syfo.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.ApplicationState

fun Routing.registerNaisApi(
        applicationState: ApplicationState
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
