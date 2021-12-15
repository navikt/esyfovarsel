package no.nav.syfo.consumer.domain

import java.time.LocalDate

data class Syketilfelledag(
    val dag: LocalDate,
    val prioritertSyketilfellebit: Syketilfellebit? = null
) {
    override fun toString(): String = "dag: " + dag + "prioritertSyketilfellebit: "+ prioritertSyketilfellebit.toString()
}
