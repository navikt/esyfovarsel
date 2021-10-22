package no.nav.syfo.metrics

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.PushGateway
import io.prometheus.client.hotspot.DefaultExports

const val JOB_METRICS_NS = "esyfovarsel"

const val MER_VEILEDNING_NOTICE_SENT = "${JOB_METRICS_NS}_mer_veiledning_notice_sent"
const val AKTIVITETSKRAV_NOTICE_SENT = "${JOB_METRICS_NS}_aktivitetskrav_notice_sent"
const val NOTICE_SENT = "${JOB_METRICS_NS}_notice_sent"

val JOB_METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM)

val COUNT_MER_VEILEDNING_NOTICE_SENT: Counter = Counter
    .builder(MER_VEILEDNING_NOTICE_SENT)
    .description("Counts the number of Mer veiledning notice sent")
    .register(JOB_METRICS_REGISTRY)

val COUNT_AKTIVITETSKRAV_NOTICE_SENT: Counter = Counter
    .builder(AKTIVITETSKRAV_NOTICE_SENT)
    .description("Counts the number of Aktivitetskrav notice sent")
    .register(JOB_METRICS_REGISTRY)

val COUNT_ALL_NOTICE_SENT: Counter = Counter
    .builder(NOTICE_SENT)
    .description("Counts the number of all types of notice sent")
    .register(JOB_METRICS_REGISTRY)

fun tellMerVeiledningVarselSendt(varslerSendt: Int) {
    COUNT_ALL_NOTICE_SENT.increment(varslerSendt.toDouble())
    COUNT_MER_VEILEDNING_NOTICE_SENT.increment(varslerSendt.toDouble())
}

fun tellAktivitetskravVarselSendt(varslerSendt: Int) {
    COUNT_ALL_NOTICE_SENT.increment(varslerSendt.toDouble())
    COUNT_AKTIVITETSKRAV_NOTICE_SENT.increment(varslerSendt.toDouble())
}

fun withPrometheus(pushGatewayUrl: String, block: () -> Unit) {
    DefaultExports.initialize()

    val durationTimer = Gauge
        .build("total_duration_seconds", "Duration of esyfovarsel-job in seconds.")
        .register(JOB_METRICS_REGISTRY.prometheusRegistry)
        .startTimer()

    try {
        block()
    } finally {
        durationTimer.setDuration()
        PushGateway(pushGatewayUrl).pushAdd(
           JOB_METRICS_REGISTRY.prometheusRegistry,
            "kubernetes-pods",
            mapOf("cronjob" to "esyfovarsel-job")
        )
    }
}
