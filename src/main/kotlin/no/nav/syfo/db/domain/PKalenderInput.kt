package no.nav.syfo.db.domain

import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.KalenderTilstand
import java.time.LocalDateTime

data class PKalenderInput(
    val eksternId: String,
    val sakId: String,
    val kalenderId: String,
    val tekst: String,
    val startTidspunkt: LocalDateTime,
    val sluttTidspunkt: LocalDateTime?,
    val kalenderavtaleTilstand: KalenderTilstand,
    val hardDeleteDate: LocalDateTime,
)
