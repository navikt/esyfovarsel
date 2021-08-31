package no.nav.syfo.api

import io.ktor.application.call
import io.ktor.response.*
import io.ktor.routing.Routing
import io.ktor.routing.get
import no.nav.syfo.metrics.METRICS_REGISTRY

fun Routing.registerPrometheusApi() {
    get("/prometheus") {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}