package no.nav.syfo.db.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class PPlanlagtVarselMerVeiledning(
    val uuid: String,
    val fnr: String,
    val type: VarselType = VarselType.MER_VEILEDNING,
    val sendingDate: LocalDate,
    val maxDate: LocalDate,
    val source: String,
    val created: LocalDateTime,
    val lastChanged: LocalDateTime,
)
