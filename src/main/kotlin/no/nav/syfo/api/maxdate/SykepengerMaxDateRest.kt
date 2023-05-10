package no.nav.syfo.api.maxdate

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import java.io.Serializable
import no.nav.syfo.auth.BrukerPrincipal
import no.nav.syfo.service.SykepengerMaxDateService
import no.nav.syfo.utils.formatDateForLetter
import org.slf4j.LoggerFactory


fun Route.registerSykepengerMaxDateRestApi(
    sykepengerMaxDateService: SykepengerMaxDateService
) {
    val log = LoggerFactory.getLogger("no.nav.syfo.api.maxdate.SykepengerMaxDateRest")
    get("/api/v1/sykepenger/maxdate") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val sykmeldtFnr = principal.fnr

        try {
            val sykepengerMaxDate = sykepengerMaxDateService.getSykepengerMaxDate(sykmeldtFnr)?.let { it1 -> formatDateForLetter(it1.forelopig_beregnet_slutt) }
            log.info("Fetched sykepengerMaxDate from database: $sykepengerMaxDate")
            call.respond(SykepengerMaxDateResponse(sykepengerMaxDate))
        } catch (e: Exception) {
            log.error("Encountered exception during fetching sykepengerMaxDate from database: ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Encountered exception during fetching max date from database: ${e.message}")
        }
    }
}

data class SykepengerMaxDateResponse(
    val maxDate: String?,
) : Serializable
