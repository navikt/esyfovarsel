package no.nav.syfo.testutil.mocks

import kotlinx.coroutines.coroutineScope
import no.nav.syfo.ApplicationState
import no.nav.syfo.consumer.domain.OppfolgingstilfellePerson
import no.nav.syfo.varsel.VarselPlanner

class MockVarselPlaner(val applicationState: ApplicationState) : VarselPlanner {
    override suspend fun processOppfolgingstilfelle(oppfolgingstilfellePerson: OppfolgingstilfellePerson) = coroutineScope {
        applicationState.running = false
    }
}
