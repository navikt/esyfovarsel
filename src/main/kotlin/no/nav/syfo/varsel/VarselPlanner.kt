package no.nav.syfo.varsel

import no.nav.syfo.consumer.domain.OppfolgingstilfellePerson

interface VarselPlanner {
    suspend fun processOppfolgingstilfelle(oppfolgingstilfellePerson: OppfolgingstilfellePerson)
}
