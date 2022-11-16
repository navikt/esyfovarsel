package no.nav.syfo.api.maxdate

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import java.io.Serializable
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.fetchForelopigBeregnetSluttPaSykepengerByFnr
import no.nav.syfo.utils.formatDateForLetter
import org.slf4j.LoggerFactory


fun Route.registerForelopigBeregnetSluttPaSykepengerRestApi(
    databaseInterface: DatabaseInterface
) {
    val log = LoggerFactory.getLogger("no.nav.syfo.api.maxdate.SykepengerMaxDateRest")
    get("/api/v1/sykepenger/maxdate") {

        val principal: JWTPrincipal = call.authentication.principal()!!
        val sykmeldtFnr = principal.payload.getClaim("pid").asString()

        try {
            val sykepengerMaxDate = databaseInterface.fetchForelopigBeregnetSluttPaSykepengerByFnr(sykmeldtFnr)?.let { it1 -> formatDateForLetter(it1) }

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
