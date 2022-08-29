package no.nav.syfo.testutil.mocks

import kotlinx.coroutines.coroutineScope
import no.nav.syfo.ApplicationState
import no.nav.syfo.planner.VarselPlannerOppfolgingstilfelle

class MockOppfolgingstilfelleVarselPlaner(val applicationState: ApplicationState) : VarselPlannerOppfolgingstilfelle {
    override val name: String = "MOCK_PLANNER"
    override suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String, orgnummer: String?) = coroutineScope {
        applicationState.running = false
    }
}
