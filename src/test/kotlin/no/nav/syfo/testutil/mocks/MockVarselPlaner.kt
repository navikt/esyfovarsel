package no.nav.syfo.testutil.mocks

import kotlinx.coroutines.coroutineScope
import no.nav.syfo.ApplicationState
import no.nav.syfo.varsel.VarselPlanner

class MockVarselPlaner(val applicationState: ApplicationState) : VarselPlanner {
    override val name: String = "MOCK_PLANNER"
    override suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String, orgnummer: String?) = coroutineScope {
        applicationState.running = false
    }
}
