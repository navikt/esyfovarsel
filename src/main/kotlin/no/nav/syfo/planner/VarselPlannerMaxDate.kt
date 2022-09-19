package no.nav.syfo.planner

import java.time.LocalDate

interface VarselPlannerMaxDate : VarselPlanner {
    val name: String
    fun processNewMaxDate(fnr: String, sykepengerMaxDate: LocalDate, source: SykepengerMaxDateSource)
}
