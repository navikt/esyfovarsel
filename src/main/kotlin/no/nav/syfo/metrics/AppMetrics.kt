package no.nav.syfo.metrics

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.DefaultExports

const val APP_METRICS_NS = "esyfovarsel"

const val ERROR_IN_PLANNER = "${APP_METRICS_NS}_error_in_planner"
const val ERROR_IN_PARSING = "${APP_METRICS_NS}_error_in_parser"
const val ERROR_IN_PROCESSING = "${APP_METRICS_NS}_error_in_processing"

val APP_METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM)

val COUNT_ERROR_IN_PLANNER: Counter = Counter
    .builder(ERROR_IN_PLANNER)
    .description("Counts the number of all errors in planner")
    .register(JOB_METRICS_REGISTRY)

val COUNT_ERROR_IN_PARSING: Counter = Counter
    .builder(ERROR_IN_PARSING)
    .description("Counts the number of all errors in planner")
    .register(JOB_METRICS_REGISTRY)

val COUNT_ERROR_IN_PROCESSING: Counter = Counter
    .builder(ERROR_IN_PROCESSING)
    .description("Counts the number of all errors in planner")
    .register(JOB_METRICS_REGISTRY)

fun tellFeilIPlanner() {
    COUNT_ERROR_IN_PLANNER.increment()
}

fun tellFeilIParsing() {
    COUNT_ERROR_IN_PARSING.increment()
}

fun tellFeilIProsessering() {
    COUNT_ERROR_IN_PROCESSING.increment()
}

fun Routing.registerPrometheusApi() {
    DefaultExports.initialize()

    get("/prometheus") {
        call.respondText(APP_METRICS_REGISTRY.scrape())
    }
}

