package no.nav.syfo.api.admin

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.util.pipeline.PipelineContext
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.service.ReplanleggingService
import java.time.LocalDate

const val urlBasePathAdmin = "/admin"
const val merVeiledningPath = "replanleggMerVeiledningVarsler"
const val aktivetskravPath = "replanleggAktivitetskravVarsler"

fun Route.registerAdminApi(
    replanleggingService: ReplanleggingService,
) {
    val fromParam = "from"
    val toParam = "to"
    get("$urlBasePathAdmin/$merVeiledningPath") {
        // from og to parametere må være på formatet "2007-12-03"
        val antallVarsler = replanleggingService.planleggMerVeiledningVarslerPaNytt(parseDate(fromParam), parseDate(toParam))
        call.respondText("Planla $antallVarsler av typen ${VarselType.MER_VEILEDNING}")
    }

    get("$urlBasePathAdmin/$aktivetskravPath") {
        // from og to parametere må være på formatet "2007-12-03"
        val antallVarsler = replanleggingService.planleggAktivitetskravVarslerPaNytt(parseDate(fromParam), parseDate(toParam))
        call.respondText("Planla $antallVarsler av typen ${VarselType.AKTIVITETSKRAV}")
    }
}

private fun PipelineContext<Unit, ApplicationCall>.parseDate(paramName: String): LocalDate {
    return LocalDate.parse(call.request.queryParameters[paramName])
}
