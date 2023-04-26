package no.nav.syfo.api.maxdate

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.consumer.dkif.DkifConsumer.Companion.NAV_PERSONIDENT_HEADER
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.service.SykepengerMaxDateService
import no.nav.syfo.utils.getPersonIdent
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.time.LocalDate

fun Route.registerSykepengerMaxDateAzureApi(
    sykepengerMaxDateService: SykepengerMaxDateService
) {
    val log = LoggerFactory.getLogger("no.nav.syfo.api.maxdate.SykepengerMaxDateAzureApi")
    get("/api/azure/v1/sykepenger/maxdate") {
        val personIdent = call.personIdent()

        try {
            val sykepengerMaxDate = sykepengerMaxDateService.getSykepengerMaxDate(personIdent.value)
            log.info("Fetched sykepengerMaxDate from database: $sykepengerMaxDate")
            call.respond(SykepengerMaxDateAzureResponse(sykepengerMaxDate))
        } catch (e: Exception) {
            log.error("Encountered exception during fetching sykepengerMaxDate from database: ${e.message}")
            call.respond(
                HttpStatusCode.InternalServerError,
                "Encountered exception during fetching max date from database: ${e.message}"
            )
        }
    }
}

data class SykepengerMaxDateAzureResponse(
    val maxDate: LocalDate?,
) : Serializable

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to get maxDate: No $NAV_PERSONIDENT_HEADER supplied in request header")
