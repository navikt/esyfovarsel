package no.nav.syfo.planner

import no.nav.syfo.access.domain.UserAccessStatus

sealed interface VarselPlanner {
    fun varselSkalLagres(userAccessStatus: UserAccessStatus): Boolean {
        return if (this is MerVeiledningVarselPlannerOppfolgingstilfelle || this is MerVeiledningVarselPlannerSyketilfellebit) {
            userAccessStatus.canUserBePhysicallyNotified || userAccessStatus.canUserBeDigitallyNotified
        } else {
            return userAccessStatus.canUserBeDigitallyNotified
        }
    }
}
