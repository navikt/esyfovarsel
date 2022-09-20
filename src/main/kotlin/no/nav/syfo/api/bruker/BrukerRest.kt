package no.nav.syfo.api.bruker

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.service.VarselSendtService
import org.slf4j.LoggerFactory
import javax.ws.rs.ForbiddenException

val urlPath39UkersVarsel = "/api/bruker/39ukersvarsel/{aktorid}"

val log = LoggerFactory.getLogger("no.nav.syfo.api.bruker.registerBrukerApi")
fun Route.registerBrukerApi(varselSendtService: VarselSendtService) {
    accept(ContentType.Application.Json) {
        get(urlPath39UkersVarsel) {
            call.respond(false)
        }
    }
}

private fun isAktorIDGyldig(aktorId: String?): Boolean {
    return aktorId?.let { id ->
        id.length == 13 && id.all { it.isDigit() }
    } ?: true
}
