package no.nav.syfo.kafka.consumers.varselbus.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.Serializable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
data class DialogmoteInnkallingArbeidstakerData(
    val varselUuid: String
) : Serializable
