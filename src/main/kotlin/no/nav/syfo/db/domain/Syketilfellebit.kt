package no.nav.syfo.db.domain

import java.time.LocalDate

data class Syketilfellebit (
    val fnr: String,
    val orgnummer: String?,
    val ressursId: String,
    val fom: LocalDate,
    val tom: LocalDate
)
