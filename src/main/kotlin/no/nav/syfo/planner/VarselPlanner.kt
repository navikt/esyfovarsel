package no.nav.syfo.planner

import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.access.domain.canUserBeNotified

sealed interface VarselPlanner {
    fun varselSkalLagres(userAccessStatus: UserAccessStatus): Boolean {
        return if (this is MerVeiledningVarselPlannerOppfolgingstilfelle || this is MerVeiledningVarselPlannerSyketilfellebit) {
            userAccessStatus.canUserBeNotified()
        } else {
            return userAccessStatus.canUserBeDigitallyNotified
        }
    }
}
