package no.nav.syfo.planner

interface VarselPlannerSyketilfellebit {
    val name: String
    suspend fun processSyketilfelle(fnr: String, orgnummer: String)
}
