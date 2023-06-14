package no.nav.syfo.api.maxdate

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.consumer.veiledertilgang.VeilederTilgangskontrollConsumer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.service.SykepengerMaxDateService
import no.nav.syfo.utils.*
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.time.LocalDate

fun Route.registerSykepengerMaxDateAzureApi(
    sykepengerMaxDateService: SykepengerMaxDateService,
    veilederTilgangskontrollConsumer: VeilederTilgangskontrollConsumer,
) {
    val log = LoggerFactory.getLogger("no.nav.syfo.api.maxdate.SykepengerMaxDateAzureApi")
    get("/api/azure/v1/sykepenger/maxdate") {
        val personIdent = call.personIdent()
        val token = call.bearerToken()
        val callId = call.getCallId()

        if (veilederTilgangskontrollConsumer.hasAccess(personIdent, token, callId)) {
            try {
                val sykepengerMaxDate = sykepengerMaxDateService.getSykepengerMaxDate(personIdent.value)
                log.info("Fetched sykepengerMaxDate from database: $sykepengerMaxDate")
                call.respond(SykepengerMaxDateAzureResponse(sykepengerMaxDate?.forelopig_beregnet_slutt))
            } catch (e: Exception) {
                log.error("Encountered exception during fetching sykepengerMaxDate from database: ${e.message}")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Encountered exception during fetching max date from database: ${e.message}",
                )
            }
        } else {
            val message = "Cannot fetch max date: Veileder has no access to person"
            log.warn("$message, {}", callIdArgument(callId))
            call.respond(HttpStatusCode.Forbidden, message)
        }
    }
}

data class SykepengerMaxDateAzureResponse(
    val maxDate: LocalDate?,
) : Serializable

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to get maxDate: No $NAV_PERSONIDENT_HEADER supplied in request header")

private fun ApplicationCall.bearerToken(): String = this.getBearerToken()
    ?: throw IllegalArgumentException("Failed to get maxDate: No Authorization header supplied")

private fun callIdArgument(callId: String) = StructuredArguments.keyValue("callId", callId)!!
