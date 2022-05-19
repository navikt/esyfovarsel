package no.nav.syfo.varsel

interface VarselPlanner {
    val name: String
    suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String, orgnummer: String?)
}
