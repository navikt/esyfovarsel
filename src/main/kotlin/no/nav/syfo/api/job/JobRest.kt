package no.nav.syfo.api.job

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.launch
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.countEntries
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

val urlTestCount = "/count"
fun Route.registerCountApi(databaseInterface: DatabaseInterface) {
    accept(ContentType.Application.Json) {
        get(urlTestCount) {
            call.respond(databaseInterface.countEntries())
        }
    }
}
