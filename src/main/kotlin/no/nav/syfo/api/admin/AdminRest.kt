package no.nav.syfo.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.service.ReplanleggingService

fun Routing.registerAdminApi(
    replanleggingService: ReplanleggingService
) {
    get("/admin/replanleggMerVeiledningVarsler") {
       replanleggingService.planleggMerVeiledningVarslerPaNytt()
        call.respond(HttpStatusCode.OK)
    }
}
