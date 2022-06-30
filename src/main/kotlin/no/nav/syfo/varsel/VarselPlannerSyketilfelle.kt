package no.nav.syfo.varsel

interface VarselPlannerSyketilfelle {
    val name: String
    suspend fun processSyketilfelle(fnr: String, orgnummer: String)
}
