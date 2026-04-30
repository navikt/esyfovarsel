package no.nav.syfo.api.job

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.accept
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import no.nav.syfo.job.ResendFailedVarslerJob
import no.nav.syfo.job.SendAktivitetspliktLetterToSentralPrintJob
import no.nav.syfo.service.microfrontend.MikrofrontendService
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

const val URL_PATH_JOB_TRIGGER = "/job/trigger"

private val log = LoggerFactory.getLogger("no.nav.syfo.api.job.JobRest")
private val isRunning = AtomicBoolean(false)

fun Route.registerJobTriggerApi(
    mikrofrontendService: MikrofrontendService,
    sendAktivitetspliktLetterToSentralPrintJob: SendAktivitetspliktLetterToSentralPrintJob,
    resendFailedVarslerJob: ResendFailedVarslerJob,
) {
    accept(ContentType.Application.Json) {
        post(URL_PATH_JOB_TRIGGER) {
            if (!isRunning.compareAndSet(false, true)) {
                log.warn("Jobb-trigger avvist: jobber kjører allerede i denne poden")
                return@post call.respond(HttpStatusCode.Conflict)
            }

            call.application.launch(Dispatchers.IO) {
                try {
                    supervisorScope {
                        launch { mikrofrontendService.findAndCloseExpiredMikrofrontends() }
                        launch { sendAktivitetspliktLetterToSentralPrintJob.sendLetterToTvingSentralPrintFromJob() }
                        launch { resendFailedVarslerJob.resendFailedBrukernotifikasjonVarsler() }
                        launch { resendFailedVarslerJob.resendFailedArbeidsgivernotifikasjonVarsler() }
                        launch { resendFailedVarslerJob.resendFailedDokDistVarsler() }
                    }
                } finally {
                    isRunning.set(false)
                }
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}
