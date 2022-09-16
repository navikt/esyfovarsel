package no.nav.syfo.kafka.consumers.infotrygd.domain

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

data class KInfotrygdSykepengedager(
    val after: After,
) {
    data class After(
        @JsonProperty("IS10_UTBET_TOM")
        @JsonAlias("is10_UTBET_TOM")
        val IS10_UTBET_TOM: String,
        @JsonProperty("IS10_MAX")
        @JsonAlias("is10_MAX")
        val IS10_MAX: String,
        @JsonProperty("F_NR")
        @JsonAlias("f_NR")
        val F_NR: String,
    )
}
