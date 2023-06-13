package no.nav.syfo.api.maxdate

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.consumer.veiledertilgang.VeilederTilgangskontrollConsumer
import no.nav.syfo.db.domain.PMaksDato
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.service.SykepengerMaxDateService
import no.nav.syfo.utils.NAV_PERSONIDENT_HEADER
import no.nav.syfo.utils.getBearerToken
import no.nav.syfo.utils.getCallId
import no.nav.syfo.utils.getPersonIdent
import org.slf4j.LoggerFactory
import java.io.Serializable

fun Route.registerSykepengerMaxDateAzureApiV2(
    sykepengerMaxDateService: SykepengerMaxDateService,
    veilederTilgangskontrollConsumer: VeilederTilgangskontrollConsumer,
) {
    val log = LoggerFactory.getLogger("no.nav.syfo.api.maxdate.SykepengerMaxDateAzureApiV2")
    get("/api/azure/v2/sykepenger/maxdate") {
        val personIdent = call.personIdent()
        val token = call.bearerToken()
        val callId = call.getCallId()

        if (veilederTilgangskontrollConsumer.hasAccess(personIdent, token, callId)) {
            try {
                val sykepengerMaxDate = sykepengerMaxDateService.getSykepengerMaxDate(personIdent.value)
                log.info("Fetched sykepengerMaxDate from database: $sykepengerMaxDate")
                call.respond(SykepengerMaxDateAzureV2Response(sykepengerMaxDate))
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

data class SykepengerMaxDateAzureV2Response(
    val maxDate: PMaksDato?,
) : Serializable

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to get maxDate: No $NAV_PERSONIDENT_HEADER supplied in request header")

private fun ApplicationCall.bearerToken(): String = this.getBearerToken()
    ?: throw IllegalArgumentException("Failed to get maxDate: No Authorization header supplied")

private fun callIdArgument(callId: String) = StructuredArguments.keyValue("callId", callId)!!
