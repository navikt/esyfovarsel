package no.nav.syfo.api.job

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.accept
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.syfo.service.microfrontend.MikrofrontendService
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

// Engangsjobb for å flytte møtebehov fra syfo-dialog til syfo-motebehov. Fjernes etter kjøring i prod.
const val URL_PATH_MOTEBEHOV_MIKROFRONTEND_BACKFILL = "/job/backfill-motebehov-mikrofrontend"

private val log = LoggerFactory.getLogger("no.nav.syfo.api.job.MotebehovMikrofrontendBackfillRest")
private val isRunning = AtomicBoolean(false)

fun Route.registerMotebehovMikrofrontendBackfillApi(mikrofrontendService: MikrofrontendService) {
    accept(ContentType.Application.Json) {
        post(URL_PATH_MOTEBEHOV_MIKROFRONTEND_BACKFILL) {
            if (!isRunning.compareAndSet(false, true)) {
                log.warn("Backfill av møtebehov-mikrofrontend avvist: kjører allerede i denne poden")
                return@post call.respond(HttpStatusCode.Conflict)
            }

            call.application.launch(Dispatchers.IO) {
                try {
                    mikrofrontendService.moveMotebehovToOwnMikrofrontend()
                } catch (e: Exception) {
                    log.error("Feil under backfill av møtebehov-mikrofrontend", e)
                } finally {
                    isRunning.set(false)
                }
            }
            call.respond(HttpStatusCode.Accepted)
        }
    }
}
