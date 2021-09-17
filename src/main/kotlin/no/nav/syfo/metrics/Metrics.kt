package no.nav.syfo.metrics

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.PushGateway
import io.prometheus.client.hotspot.DefaultExports

const val METRICS_NS = "esyfovarsel"

const val MER_VEILEDNING_NOTICE_SENT = "${METRICS_NS}_mer_veiledning_notice_sent"
const val AKTIVITETSPLIKT_NOTICE_SENT = "${METRICS_NS}_aktivitetsplikt_notice_sent"
const val NOTICE_SENT = "${METRICS_NS}_notice_sent"
const val ERROR_IN_PLANNER = "${METRICS_NS}_error_in_planner"
const val ERROR_IN_PARSING = "${METRICS_NS}_error_in_parser"
const val ERROR_IN_PROCESSING = "${METRICS_NS}_error_in_processing"

val METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

val COUNT_MER_VEILEDNING_NOTICE_SENT: Counter = Counter
    .builder(MER_VEILEDNING_NOTICE_SENT)
    .description("Counts the number of Mer veiledning notice sent")
    .register(METRICS_REGISTRY)

val COUNT_AKTIVITETSPLIKT_NOTICE_SENT: Counter = Counter
    .builder(AKTIVITETSPLIKT_NOTICE_SENT)
    .description("Counts the number of Aktivitetsplikt notice sent")
    .register(METRICS_REGISTRY)

val COUNT_ALL_NOTICE_SENT: Counter = Counter
    .builder(NOTICE_SENT)
    .description("Counts the number of all types of notice sent")
    .register(METRICS_REGISTRY)

val COUNT_ERROR_IN_PLANNER: Counter = Counter
    .builder(ERROR_IN_PLANNER)
    .description("Counts the number of all errors in planner")
    .register(METRICS_REGISTRY)

val COUNT_ERROR_IN_PARSING: Counter = Counter
    .builder(ERROR_IN_PARSING)
    .description("Counts the number of all errors in planner")
    .register(METRICS_REGISTRY)

val COUNT_ERROR_IN_PROCESSING: Counter = Counter
    .builder(ERROR_IN_PROCESSING)
    .description("Counts the number of all errors in planner")
    .register(METRICS_REGISTRY)

fun tellMerVeiledningVarselSendt(varslerSendt: Int) {
    COUNT_ALL_NOTICE_SENT.increment(varslerSendt.toDouble())
    COUNT_MER_VEILEDNING_NOTICE_SENT.increment(varslerSendt.toDouble())
}

fun tellAktivitetskravVarselSendt(varslerSendt: Int) {
    COUNT_ALL_NOTICE_SENT.increment(varslerSendt.toDouble())
    COUNT_AKTIVITETSPLIKT_NOTICE_SENT.increment(varslerSendt.toDouble())
}

fun tellFeilIPlanner() {
    COUNT_ERROR_IN_PLANNER.increment()
}

fun tellFeilIParsing() {
    COUNT_ERROR_IN_PARSING.increment()
}

fun tellFeilIProsessering() {
    COUNT_ERROR_IN_PROCESSING.increment()
}

fun withPrometheus(pushGatewayUrl: String, block: () -> Unit) {
    DefaultExports.initialize()

    val durationTimer = Gauge
        .build("total_duration_seconds", "Duration of esyfovarsel-job in seconds.")
        .register(METRICS_REGISTRY.prometheusRegistry)
        .startTimer()

    try {
        block()
    } finally {
        durationTimer.setDuration()
        PushGateway(pushGatewayUrl).pushAdd(
           METRICS_REGISTRY.prometheusRegistry,
            "kubernetes-pods",
            mapOf("cronjob" to "esyfovarsel-job")
        )
    }
}

fun Routing.registerPrometheusApi() {
    get("/prometheus") {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}

