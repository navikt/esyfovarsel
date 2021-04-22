package no.nav.syfo.consumer.domain

import java.time.LocalDate

data class Syketilfelledag(
    val dag: LocalDate,
    val prioritertSyketilfellebit: Syketilfellebit?
)
