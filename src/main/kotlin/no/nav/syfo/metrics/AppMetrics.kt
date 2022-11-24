package no.nav.syfo.metrics

import io.ktor.application.call
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.DefaultExports

const val METRICS_NS = "esyfovarsel"

const val MER_VEILEDNING_PLANNED = "${METRICS_NS}_mer_veiledning_planned"
const val AKTIVITETSKRAV_PLANNED = "${METRICS_NS}_aktivitetskrav_planned"

const val MER_VEILEDNING_NOTICE_SENT = "${METRICS_NS}_mer_veiledning_notice_sent"
const val AKTIVITETSKRAV_NOTICE_SENT = "${METRICS_NS}_aktivitetskrav_notice_sent"
const val SVAR_MOTEBEHOV_NOTICE_SENT = "${METRICS_NS}_svar_motebehov_notice_sent"
const val NOTICE_SENT = "${METRICS_NS}_notice_sent"


val METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM)

val COUNT_MER_VEILEDNING_PLANNED: Counter = Counter
    .builder(MER_VEILEDNING_PLANNED)
    .description("Counts the number of planned notice of type Mer veiledning")
    .register(METRICS_REGISTRY)

val COUNT_AKTIVITETSKRAV_PLANNED: Counter = Counter
    .builder(AKTIVITETSKRAV_PLANNED)
    .description("Counts the number of planned notice of type Aktivitetskrav")
    .register(METRICS_REGISTRY)

val COUNT_MER_VEILEDNING_NOTICE_SENT: Counter = Counter
    .builder(MER_VEILEDNING_NOTICE_SENT)
    .description("Counts the number of Mer veiledning notice sent")
    .register(METRICS_REGISTRY)

val COUNT_AKTIVITETSKRAV_NOTICE_SENT: Counter = Counter
    .builder(AKTIVITETSKRAV_NOTICE_SENT)
    .description("Counts the number of Aktivitetskrav notice sent")
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

fun tellAktivitetskravVarselSendt(varslerSendt: Int) {
    COUNT_ALL_NOTICE_SENT.increment(varslerSendt.toDouble())
    COUNT_AKTIVITETSKRAV_NOTICE_SENT.increment(varslerSendt.toDouble())
}

fun tellSvarMotebehovVarselSendt(varslerSendt: Int) {
    COUNT_ALL_NOTICE_SENT.increment(varslerSendt.toDouble())
    COUNT_SVAR_MOTEBEHOV_NOTICE_SENT.increment(varslerSendt.toDouble())
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
