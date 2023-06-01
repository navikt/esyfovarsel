package no.nav.syfo.api.job

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.launch
import no.nav.syfo.job.VarselSender
import no.nav.syfo.service.microfrontend.MikrofrontendService

const val urlPathJobTrigger = "/job/trigger"

fun Route.registerJobTriggerApi(varselSender: VarselSender, mikrofrontendService: MikrofrontendService) {
    accept(ContentType.Application.Json) {
        post(urlPathJobTrigger) {
            call.respond(HttpStatusCode.OK)
            launch {
                varselSender.sendVarsler()
            }
            launch {
                mikrofrontendService.findAndCloseExpiredMikrofrontends()
            }
        }
    }
}
