package no.nav.syfo.api.admin

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.service.ReplanleggingService
import java.time.LocalDate

const val urlBasePathAdmin = "/admin"
const val aktivetskravPath = "replanleggAktivitetskravVarsler"

fun Route.registerAdminApi(
    replanleggingService: ReplanleggingService
) {
    val fromParam = "from"
    val toParam = "to"

    get("$urlBasePathAdmin/$aktivetskravPath") {
        // from og to parametere må være på formatet "2007-12-03"
        val antallVarsler = replanleggingService.planleggAktivitetskravVarslerPaNytt(parseDate(fromParam), parseDate(toParam))
        call.respondText("Planla $antallVarsler av typen ${VarselType.AKTIVITETSKRAV}")
    }
}

private fun PipelineContext<Unit, ApplicationCall>.parseDate(paramName: String): LocalDate {
    return LocalDate.parse(call.request.queryParameters[paramName])
}