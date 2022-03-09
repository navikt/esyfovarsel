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

const val METRICS_NS = "esyfovarsel"

const val ERROR_IN_PLANNER = "${METRICS_NS}_error_in_planner"
const val ERROR_IN_PARSING = "${METRICS_NS}_error_in_parser"
const val MER_VEILEDNING_PLANNED = "${METRICS_NS}_mer_veiledning_planned"
const val AKTIVITETSKRAV_PLANNED = "${METRICS_NS}_aktivitetskrav_planned"

const val MER_VEILEDNING_NOTICE_SENT = "${METRICS_NS}_mer_veiledning_notice_sent"
const val AKTIVITETSKRAV_NOTICE_SENT = "${METRICS_NS}_aktivitetskrav_notice_sent"
const val NOTICE_SENT = "${METRICS_NS}_notice_sent"


val METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM)

val COUNT_ERROR_IN_PLANNER: Counter = Counter
    .builder(ERROR_IN_PLANNER)
    .description("Counts the number of all errors in planner")
    .register(METRICS_REGISTRY)

val COUNT_ERROR_IN_PARSING: Counter = Counter
    .builder(ERROR_IN_PARSING)
    .description("Counts the number of all errors in parsing")
    .register(METRICS_REGISTRY)

val COUNT_MER_VEILEDNING_PLANNED: Counter = Counter
    .builder(MER_VEILEDNING_PLANNED)
    .description("Counts the number of planned notice of type Mer veiledning")
    .register(METRICS_REGISTRY)

val COUNT_AKTIVITETSKRAV_PLANNED: Counter = Counter
    .builder(AKTIVITETSKRAV_PLANNED)
    .description("Counts the number of planned notice of type Aktivitetskrav")
    .register(METRICS_REGISTRY)


fun tellFeilIPlanner() {
    COUNT_ERROR_IN_PLANNER.increment()
}

fun tellFeilIParsing() {
    COUNT_ERROR_IN_PARSING.increment()
}

val COUNT_MER_VEILEDNING_NOTICE_SENT: Counter = Counter
    .builder(MER_VEILEDNING_NOTICE_SENT)
    .description("Counts the number of Mer veiledning notice sent")
    .register(METRICS_REGISTRY)

val COUNT_AKTIVITETSKRAV_NOTICE_SENT: Counter = Counter
    .builder(AKTIVITETSKRAV_NOTICE_SENT)
    .description("Counts the number of Aktivitetskrav notice sent")
    .register(METRICS_REGISTRY)

val COUNT_ALL_NOTICE_SENT: Counter = Counter
    .builder(NOTICE_SENT)
    .description("Counts the number of all types of notice sent")
    .register(METRICS_REGISTRY)

fun tellMerVeiledningVarselSendt(varslerSendt: Int) {
    COUNT_ALL_NOTICE_SENT.increment(varslerSendt.toDouble())
    COUNT_MER_VEILEDNING_NOTICE_SENT.increment(varslerSendt.toDouble())
}

fun tellAktivitetskravVarselSendt(varslerSendt: Int) {
    COUNT_ALL_NOTICE_SENT.increment(varslerSendt.toDouble())
    COUNT_AKTIVITETSKRAV_NOTICE_SENT.increment(varslerSendt.toDouble())
}

fun tellMerVeiledningPlanlagt() {
    COUNT_MER_VEILEDNING_PLANNED.increment()
}

fun tellAktivitetskravPlanlagt() {
    COUNT_AKTIVITETSKRAV_PLANNED.increment()
}

fun Routing.registerPrometheusApi() {
    DefaultExports.initialize()

    get("/prometheus") {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
