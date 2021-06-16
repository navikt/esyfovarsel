package no.nav.syfo.varsel

import no.nav.syfo.consumer.domain.OppfolgingstilfellePerson

interface VarselPlanner {
    fun processOppfolgingstilfelle(oppfolgingstilfellePerson: OppfolgingstilfellePerson, fnr: String)
}
