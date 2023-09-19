package no.nav.syfo.kafka.producers.mineside_microfrontend

import java.time.LocalDate

data class MikrofrontendSynlighet(
    val synligFor: String,
    val tjeneste: Tjeneste,
    val synligTom: LocalDate?
)

enum class Tjeneste {
    DIALOGMOTE, AKTIVITETSKRAV
}
