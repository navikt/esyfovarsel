package no.nav.syfo.metrics

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.DefaultExports

const val METRICS_NS = "esyfovarsel"

const val MER_VEILEDNING_NOTICE_SENT = "${METRICS_NS}_mer_veiledning_notice_sent"
const val SVAR_MOTEBEHOV_NOTICE_SENT = "${METRICS_NS}_svar_motebehov_notice_sent"
const val NOTICE_SENT = "${METRICS_NS}_notice_sent"

val METRICS_REGISTRY =
    PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM)

val COUNT_MER_VEILEDNING_NOTICE_SENT: Counter = Counter
    .builder(MER_VEILEDNING_NOTICE_SENT)
    .description("Counts the number of Mer veiledning notice sent")
    .register(METRICS_REGISTRY)

val COUNT_SVAR_MOTEBEHOV_NOTICE_SENT: Counter = Counter
    .builder(SVAR_MOTEBEHOV_NOTICE_SENT)
    .description("Counts the number of Svar møtebehov notice sent")
    .register(METRICS_REGISTRY)

val COUNT_ALL_NOTICE_SENT: Counter = Counter
    .builder(NOTICE_SENT)
    .description("Counts the number of all types of notice sent")
    .register(METRICS_REGISTRY)

fun tellMerVeiledningVarselSendt(varslerSendt: Int) {
    COUNT_ALL_NOTICE_SENT.increment(varslerSendt.toDouble())
    COUNT_MER_VEILEDNING_NOTICE_SENT.increment(varslerSendt.toDouble())
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
