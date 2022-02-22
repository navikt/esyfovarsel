package no.nav.syfo.syketilfelle.domain

import java.time.LocalDate

data class Syketilfelledag(
    val dag: LocalDate,
    val prioritertSyketilfellebit: Syketilfellebit?,
    val syketilfellebiter: List<Syketilfellebit>
)
