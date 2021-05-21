package no.nav.syfo.testutil.mocks

import no.nav.syfo.ApplicationState
import no.nav.syfo.consumer.domain.OppfolgingstilfellePerson
import no.nav.syfo.varsel.VarselPlanner

class MockVarselPlaner(val applicationState: ApplicationState) : VarselPlanner {
    override fun processOppfolgingstilfelle(oppfolgingstilfellePerson: OppfolgingstilfellePerson) {
        applicationState.running = false
    }
}
