package no.nav.syfo.api.job

import io.ktor.application.*
import no.nav.syfo.job.VarselSender
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

val urlPathJobTrigger = "/job/trigger"

fun Route.registerJobTriggerApi(varselSender: VarselSender) {
    accept(ContentType.Application.Json) {
        post(urlPathJobTrigger) {
            call.respond(varselSender.sendVarsler())
        }
    }
}
