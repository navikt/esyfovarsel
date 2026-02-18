package no.nav.syfo.db.domain

import java.time.LocalDateTime
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.KalenderTilstand

data class PKalenderInput(
    val sakId: String,
    val eksternId: String,
    val grupperingsid: String,
    val merkelapp: String,
    val kalenderId: String,
    val tekst: String,
    val startTidspunkt: LocalDateTime,
    val sluttTidspunkt: LocalDateTime?,
    val kalenderavtaleTilstand: KalenderTilstand,
    val hardDeleteDate: LocalDateTime?,
)
