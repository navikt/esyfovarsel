package no.nav.syfo.service

import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.dkif.DkifConsumer

class AccessControlService(
    val dkifConsumer: DkifConsumer,
) {
    suspend fun getUserAccessStatus(fnr: String): UserAccessStatus {
        // status i KRR: [ikke reservert + e-post eller mobilnummer verifisert siste 18mnd]
        val isKanVarsles = dkifConsumer.person(fnr)?.kanVarsles

        return UserAccessStatus(
            fnr,
            true == isKanVarsles,
        )
    }

    suspend fun canUserBeNotifiedByEmailOrSMS(fnr: String) = getUserAccessStatus(fnr).canUserBeDigitallyNotified
}
