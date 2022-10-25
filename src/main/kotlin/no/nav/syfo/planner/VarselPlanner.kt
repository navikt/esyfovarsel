package no.nav.syfo.planner

import no.nav.syfo.access.domain.UserAccessStatus

interface VarselPlanner {
    val name: String
    suspend fun processSyketilfelle(fnr: String, orgnummer: String?)
    fun varselSkalLagres(userAccessStatus: UserAccessStatus): Boolean {
        return userAccessStatus.canUserBeDigitallyNotified
    }
}
