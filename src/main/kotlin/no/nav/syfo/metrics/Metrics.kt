package no.nav.syfo.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.syfo.db.domain.VarselType.AKTIVITETSKRAV
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING

const val METRICS_NS = "esyfovarsel"

const val MER_VEILEDNING_NOTICE_SENT = "${METRICS_NS}_mer_veiledning_notice_sent"
const val AKTIVITETSPLIKT_NOTICE_SENT = "${METRICS_NS}_aktivitetsplikt_notice_sent"
const val NOTICE_SENT = "${METRICS_NS}_notice_sent"

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


fun tellVarselSendt(varselType: String) {
    COUNT_ALL_NOTICE_SENT.increment()
    when(varselType){
        MER_VEILEDNING.name -> COUNT_MER_VEILEDNING_NOTICE_SENT.increment()
        AKTIVITETSKRAV.name -> COUNT_AKTIVITETSPLIKT_NOTICE_SENT.increment()
    }
}