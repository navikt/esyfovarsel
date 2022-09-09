package no.nav.syfo.api.job

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import no.nav.syfo.job.VarselSender

val urlPathJobTrigger = "/job/trigger"

fun Route.registerJobTriggerApi(varselSender: VarselSender) {
    accept(ContentType.Application.Json) {
        post(urlPathJobTrigger) {
            call.respond(HttpStatusCode.OK)
            launch {
                varselSender.sendVarsler()
            }
        }
    }
}
