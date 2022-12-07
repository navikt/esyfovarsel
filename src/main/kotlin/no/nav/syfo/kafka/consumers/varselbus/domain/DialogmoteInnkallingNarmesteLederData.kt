package no.nav.syfo.kafka.consumers.varselbus.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.Serializable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
data class DialogmoteInnkallingNarmesteLederData(
    val narmesteLederNavn: String?,
) : Serializable
