package no.nav.syfo.db.domain

import java.time.LocalDate

enum class VarselType {
    MER_VEILEDNING
}

const val UTSENDING_FEILET = "UTSENDING_FEILET"

data class PlanlagtVarsel(
    val fnr: String,
    val aktorId: String,
    val orgnummer: String?,
    val sykmeldingerId: Set<String>,
    val type: VarselType,
    val utsendingsdato: LocalDate = LocalDate.now()
)
