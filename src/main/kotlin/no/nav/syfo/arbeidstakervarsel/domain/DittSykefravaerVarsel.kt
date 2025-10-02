package no.nav.syfo.arbeidstakervarsel.domain

import java.time.Instant
import java.util.*

enum class DittSykefravaerVarselType {
    SEND,
    FERDIGSTILL
}

data class DittSykefravaerVarsel(
    val id: UUID,
    val varselType: DittSykefravaerVarselType,
    val tekst: String,
    val lenke: String?,
    val lukkbar: Boolean,
    val meldingType: String,
    val synligFremTil: Instant?,
    val scheduledRetry: Boolean = true,
)
