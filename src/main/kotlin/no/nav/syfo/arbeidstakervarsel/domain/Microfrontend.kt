package no.nav.syfo.arbeidstakervarsel.domain

import java.time.LocalDate

enum class MicrofrontendAction {
    ENABLE,
    DISABLE
}

enum class MicrofrontendType {
    DIALOGMOTE, AKTIVITETSKRAV, MER_OPPFOLGING
}

data class Microfrontend(
    val uuid: String,
    val action: MicrofrontendAction,
    val microfrontendType: MicrofrontendType,
    val synligTom: LocalDate,
)
