package no.nav.syfo.planner

interface VarselPlanner {
    val name: String
    suspend fun processSyketilfelle(fnr: String, orgnummer: String?)
}
