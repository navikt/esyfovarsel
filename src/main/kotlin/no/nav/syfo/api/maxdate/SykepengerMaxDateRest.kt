package no.nav.syfo.api.maxdate

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.syfo.auth.BrukerPrincipal
import no.nav.syfo.service.SykepengerMaxDateService
import no.nav.syfo.utils.formatDateForLetter
import org.slf4j.LoggerFactory
import java.io.Serializable

fun Route.registerSykepengerMaxDateRestApi(
    sykepengerMaxDateService: SykepengerMaxDateService,
) {
    val log = LoggerFactory.getLogger("no.nav.syfo.api.maxdate.SykepengerMaxDateRest")
    get("/api/v1/sykepenger/maxdate") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val sykmeldtFnr = principal.fnr
        val isoFormat = call.request.queryParameters["isoformat"]?.toBoolean() ?: false

        try {
            val sykepengerMaxDate = sykepengerMaxDateService.getSykepengerMaxDate(sykmeldtFnr)
            val maxDate = sykepengerMaxDate?.let {
                if (isoFormat) it.forelopig_beregnet_slutt.toString()
                else formatDateForLetter(it.forelopig_beregnet_slutt)
            }
            val utbetaltTom = sykepengerMaxDate?.let {
                if (isoFormat) it.utbetalt_tom.toString()
                else formatDateForLetter(it.utbetalt_tom)
            }
            log.info("Fetched sykepengerMaxDate from database: ${sykepengerMaxDate?.forelopig_beregnet_slutt}")
            call.respond(SykepengerMaxDateResponse(maxDate, utbetaltTom))
        } catch (e: Exception) {
            log.error("Encountered exception during fetching sykepengerMaxDate from database: ${e.message}")
            call.respond(
                HttpStatusCode.InternalServerError,
                "Encountered exception during fetching max date from database: ${e.message}",
            )
        }
    }
}


data class SykepengerMaxDateResponse(
    val maxDate: String?,
    val utbetaltTom: String?,
) : Serializable
