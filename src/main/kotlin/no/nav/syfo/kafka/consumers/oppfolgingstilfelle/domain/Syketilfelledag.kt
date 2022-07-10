package no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain

import no.nav.syfo.syketilfelle.domain.Syketilfellebit
import java.time.LocalDate

data class Syketilfelledag(
    val dag: LocalDate,
    val prioritertSyketilfellebit: Syketilfellebit? = null
) {
    override fun toString(): String = "dag: " + dag + ", prioritertSyketilfellebit: " + "[" + prioritertSyketilfellebit.toString() + "]"
}
