package no.nav.syfo.varsel

interface VarselPlannerSyketilfellebit {
    val name: String
    suspend fun processSyketilfelle(fnr: String, orgnummer: String)
}
