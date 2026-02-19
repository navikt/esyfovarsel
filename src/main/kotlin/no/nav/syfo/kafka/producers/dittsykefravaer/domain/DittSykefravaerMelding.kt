package no.nav.syfo.kafka.producers.dittsykefravaer.domain

import java.time.Instant

data class DittSykefravaerMelding(
    val opprettMelding: OpprettMelding?,
    val lukkMelding: LukkMelding?,
    val fnr: String,
)

data class LukkMelding(
    val timestamp: Instant,
)

enum class Variant {
    INFO,
}

data class OpprettMelding(
    val tekst: String,
    val lenke: String?,
    val variant: Variant,
    val lukkbar: Boolean,
    val meldingType: String,
    val synligFremTil: Instant?,
)
