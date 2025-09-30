package no.nav.syfo.metrics

import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.metrics.model.registry.PrometheusRegistry

const val METRICS_NS = "esyfovarsel"

const val MER_VEILEDNING_NOTICE_SENT = "${METRICS_NS}_mer_veiledning_notice_sent"
const val SVAR_MOTEBEHOV_NOTICE_SENT = "${METRICS_NS}_svar_motebehov_notice_sent"
const val KARTLEGGINGSSPORSMAL_NOTICE_SENT = "${METRICS_NS}_kartleggingssporsmal_notice_sent"
const val NOTICE_SENT = "${METRICS_NS}_notice_sent"
const val CALL_PDL_SUCCESS = "${METRICS_NS}_call_pdl_success_count"
const val CALL_PDL_FAIL = "${METRICS_NS}_call_pdl_fail_count"

val METRICS_REGISTRY =
    PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)

val COUNT_MER_VEILEDNING_NOTICE_SENT: Counter = Counter
    .builder(MER_VEILEDNING_NOTICE_SENT)
    .description("Counts the number of Mer veiledning notice sent")
    .register(METRICS_REGISTRY)

val COUNT_KARTLEGGINGSSPORSMAL_NOTICE_SENT: Counter = Counter
    .builder(KARTLEGGINGSSPORSMAL_NOTICE_SENT)
    .description("Counts the number of kartleggingssporsmal notice sent")
    .register(METRICS_REGISTRY)

val COUNT_SVAR_MOTEBEHOV_NOTICE_SENT: Counter = Counter
    .builder(SVAR_MOTEBEHOV_NOTICE_SENT)
    .description("Counts the number of Svar møtebehov notice sent")
    .register(METRICS_REGISTRY)

val COUNT_ALL_NOTICE_SENT: Counter = Counter
    .builder(NOTICE_SENT)
    .description("Counts the number of all types of notice sent")
    .register(METRICS_REGISTRY)

val COUNT_CALL_PDL_SUCCESS: Counter = Counter.builder(CALL_PDL_SUCCESS)
    .description("Counts the number of successful calls to pdl")
    .register(METRICS_REGISTRY)

val COUNT_CALL_PDL_FAIL: Counter = Counter.builder(CALL_PDL_FAIL)
    .description("Counts the number of failed calls to pdl")
    .register(METRICS_REGISTRY)

fun tellMerVeiledningVarselSendt() {
    COUNT_ALL_NOTICE_SENT.increment()
    COUNT_MER_VEILEDNING_NOTICE_SENT.increment()
}

fun countKartleggingssporsmalVarselSendt() {
    COUNT_ALL_NOTICE_SENT.increment()
    COUNT_KARTLEGGINGSSPORSMAL_NOTICE_SENT.increment()
}

fun tellSvarMotebehovVarselSendt(varslerSendt: Int) {
    COUNT_ALL_NOTICE_SENT.increment(varslerSendt.toDouble())
    COUNT_SVAR_MOTEBEHOV_NOTICE_SENT.increment(varslerSendt.toDouble())
}

fun Routing.registerPrometheusApi() {
    DefaultExports.initialize()

    get("/prometheus") {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
