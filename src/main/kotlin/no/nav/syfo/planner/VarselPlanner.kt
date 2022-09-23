package no.nav.syfo.planner

import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.access.domain.canUserBeNotified

interface VarselPlanner {
    val name: String
    suspend fun processSyketilfelle(fnr: String, orgnummer: String?)
    fun varselSkalLagres(userAccessStatus: UserAccessStatus): Boolean {
        return if (this is MerVeiledningVarselPlanner) {
            userAccessStatus.canUserBeNotified()
        } else {
            return userAccessStatus.canUserBeDigitallyNotified
        }
    }
}
