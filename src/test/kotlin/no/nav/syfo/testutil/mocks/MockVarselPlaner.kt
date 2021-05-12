package no.nav.syfo.testutil.mocks

import no.nav.syfo.ApplicationState
import no.nav.syfo.consumer.domain.Oppfolgingstilfelle
import no.nav.syfo.varsel.VarselPlanner

class MockVarselPlaner(val applicationState: ApplicationState) : VarselPlanner {
    override fun processOppfolgingstilfelle(oppfolgingstilfelle: Oppfolgingstilfelle) {
        applicationState.running = false
    }
}
