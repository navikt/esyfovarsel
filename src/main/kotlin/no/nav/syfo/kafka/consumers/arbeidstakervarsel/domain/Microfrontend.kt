package no.nav.syfo.kafka.consumers.arbeidstakervarsel.domain

import java.time.LocalDate

enum class MicroFrontendEventType {
    ENABLE,
    DISABLE
}

data class Microfrontend(
    val uuid: String,
    val microfrontendId: String,
    val eventType: MicroFrontendEventType,
    val synligTilOgMed: LocalDate,
)
