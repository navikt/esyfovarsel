package no.nav.syfo.planner

interface VarselPlannerOppfolgingstilfelle: VarselPlanner {
    val name: String
    suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String, orgnummer: String?)
}
