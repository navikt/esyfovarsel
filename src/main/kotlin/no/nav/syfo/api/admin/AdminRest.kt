package no.nav.syfo.api.admin

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import no.nav.syfo.consumer.DkifConsumer
import no.nav.syfo.service.ReplanleggingService
import java.time.LocalDate

fun Route.registerAdminApi(
    replanleggingService: ReplanleggingService
) {
    val fromParam = "from"
    val toParam = "to"
    get("/admin/replanleggMerVeiledningVarsler") {
        // from og to parametere må være på formatet "2007-12-03"
        replanleggingService.planleggMerVeiledningVarslerPaNytt(parseDate(fromParam), parseDate(toParam))
        call.respond(HttpStatusCode.OK)
    }

    get("/admin/replanleggAktivitetskravVarsler") {
        // from og to parametere må være på formatet "2007-12-03"
        replanleggingService.planleggAktivitetskravVarslerPaNytt(parseDate(fromParam), parseDate(toParam))
        call.respond(HttpStatusCode.OK)
    }
}

private fun PipelineContext<Unit, ApplicationCall>.parseDate(paramName: String): LocalDate {
    return LocalDate.parse(call.request.queryParameters[paramName])
}