package no.nav.syfo.kafka.consumers.infotrygd.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class KInfotrygdSykepengedager(
    val after: After,
) {
    data class After(
        @JsonProperty("MAX_DATO")
        val MAX_DATO: String,
        @JsonProperty("UTBET_TOM")
        val UTBET_TOM: String? = null,
        @JsonProperty("F_NR")
        val F_NR: String,
    )
}
