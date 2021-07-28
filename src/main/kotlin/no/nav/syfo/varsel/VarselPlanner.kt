package no.nav.syfo.varsel

interface VarselPlanner {
    suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String)
}
