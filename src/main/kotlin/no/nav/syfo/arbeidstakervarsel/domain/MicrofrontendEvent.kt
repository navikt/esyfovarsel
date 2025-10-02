package no.nav.syfo.arbeidstakervarsel.domain

import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import java.time.LocalDate

enum class MicrofrontendAction {
    ENABLE,
    DISABLE
}

enum class MicrofrontendType {
    DIALOGMOTE, AKTIVITETSKRAV, MER_OPPFOLGING
}

fun MicrofrontendType.toTjeneste(): Tjeneste = when (this) {
    MicrofrontendType.DIALOGMOTE -> Tjeneste.DIALOGMOTE
    MicrofrontendType.AKTIVITETSKRAV -> Tjeneste.AKTIVITETSKRAV
    MicrofrontendType.MER_OPPFOLGING -> Tjeneste.MER_OPPFOLGING
}

fun MicrofrontendType.toMicrofrontendId(): String = when (this) {
    MicrofrontendType.DIALOGMOTE -> "syfo-dialog"
    MicrofrontendType.AKTIVITETSKRAV -> "syfo-aktivitetskrav"
    MicrofrontendType.MER_OPPFOLGING -> "syfo-mer-oppfolging"
}

data class MicrofrontendEvent(
    val uuid: String,
    val action: MicrofrontendAction,
    val microfrontendType: MicrofrontendType,
    val synligTom: LocalDate,
)
