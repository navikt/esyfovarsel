package no.nav.syfo.kafka.consumers.varselbus.domain

import java.io.Serializable

data class DialogmoteInnkallingNarmesteLederData(
    val narmesteLederNavn: String?,
) : Serializable
