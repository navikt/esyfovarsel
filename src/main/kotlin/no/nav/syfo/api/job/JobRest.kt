package no.nav.syfo.api.job

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.accept
import io.ktor.server.routing.post
import kotlinx.coroutines.launch
import no.nav.syfo.job.ResendFailedVarslerJob
import no.nav.syfo.job.SendAktivitetspliktLetterToSentralPrintJob
import no.nav.syfo.service.microfrontend.MikrofrontendService

const val urlPathJobTrigger = "/job/trigger"

fun Route.registerJobTriggerApi(
    mikrofrontendService: MikrofrontendService,
    sendAktivitetspliktLetterToSentralPrintJob: SendAktivitetspliktLetterToSentralPrintJob,
    resendFailedVarslerJob: ResendFailedVarslerJob
) {
    accept(ContentType.Application.Json) {
        post(urlPathJobTrigger) {
            call.respond(HttpStatusCode.OK)
            launch {
                mikrofrontendService.findAndCloseExpiredMikrofrontends()
            }
            launch {
                sendAktivitetspliktLetterToSentralPrintJob.sendLetterToTvingSentralPrintFromJob()
            }
            launch {
                resendFailedVarslerJob.resendFailedBrukernotifikasjonVarsler()
            }
            launch {
                resendFailedVarslerJob.resendFailedArbeidsgivernotifikasjonVarsler()
            }
            launch {
                resendFailedVarslerJob.resendFailedDokDistVarsler()
            }
        }
    }
}
