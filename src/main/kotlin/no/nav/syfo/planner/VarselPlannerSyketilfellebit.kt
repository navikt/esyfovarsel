package no.nav.syfo.planner

interface VarselPlannerSyketilfellebit : VarselPlanner {
    val name: String
    suspend fun processSyketilfelle(fnr: String, orgnummer: String)
}
