package no.nav.syfo.api.admin

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.service.ReplanleggingService

fun Route.registerAdminApi(
    replanleggingService: ReplanleggingService
) {
    get("/admin/replanleggMerVeiledningVarsler") {
        replanleggingService.planleggMerVeiledningVarslerPaNytt()
        call.respond(HttpStatusCode.OK)
    }

    get("/admin/replanleggAktivitetskravVarsler") {
        replanleggingService.planleggAktivitetskravVarslerPaNytt()
        call.respond(HttpStatusCode.OK)
    }
}
