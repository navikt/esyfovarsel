package no.nav.syfo.kafka.producers.mineside_microfrontend

import com.fasterxml.jackson.annotation.JsonProperty

enum class MinSideEvent {
    ENABLE,
    DISABLE
}

data class MinSideRecord(
    @JsonProperty("@action") val eventType: String,
    @JsonProperty("ident") val fnr: String,
    @JsonProperty("microfrontend_id") val microfrontendId: String,
    @JsonProperty("sensitivitet") val sensitivitet: String = "high",
    @JsonProperty("@initiated_by") val initiatedBy: String = "team-esyfo"
)
