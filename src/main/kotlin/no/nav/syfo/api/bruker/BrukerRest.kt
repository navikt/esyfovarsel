package no.nav.syfo.api.bruker

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.service.VarselSendtService
import javax.ws.rs.ForbiddenException

val urlPath39UkersVarsel = "/api/bruker/39ukersvarsel/{aktorid}"

fun Route.registerBrukerApi(varselSendtService: VarselSendtService) {
    accept(ContentType.Application.Json) {
        get(urlPath39UkersVarsel) {
            val principal: JWTPrincipal = call.authentication.principal()!!
            val innloggetFnr = principal.payload.getClaim("pid").asString()
            val aktorId = call.parameters["aktorid"]

            if (!isAktorIDGyldig(aktorId)) {
                call.respond(HttpStatusCode.BadRequest, "Manglende eller ugyldig aktorId i request")
            } else {
                try {
                    val erSendtOgGyldig = varselSendtService.varselErSendtOgGyldig(innloggetFnr, aktorId!!, VarselType.MER_VEILEDNING)
                    call.respond(erSendtOgGyldig)
                } catch (e: ForbiddenException) {
                    call.respond(HttpStatusCode.Forbidden, "Ikke autorisert")
                } catch (e: RuntimeException) {
                    call.respond(HttpStatusCode.InternalServerError, "Feil ved henting av aktorid")
                }
            }
        }
    }
}

private fun isAktorIDGyldig(aktorId: String?): Boolean {
    return aktorId?.let { id ->
        id.length == 13 && id.all { it.isDigit() }
    } ?: true
}
