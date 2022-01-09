package no.nav.syfo.api.job

import no.nav.syfo.job.VarselSender
import io.ktor.http.*
import io.ktor.routing.*

val urlPathJobTrigger = "/job/trigger"

fun Route.registerJobTriggerApi(varselSender: VarselSender) {
    accept(ContentType.Application.Json) {
        get(urlPathJobTrigger) {
            varselSender.sendVarsler()
        }
    }
}