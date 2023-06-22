package no.nav.syfo.service

import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.consumer.pdl.PdlConsumer

class AccessControlService(val pdlConsumer: PdlConsumer, val dkifConsumer: DkifConsumer) {
    fun getUserAccessStatus(fnr: String): UserAccessStatus {
        val isKode6Eller7 = pdlConsumer.isBrukerGradertForInformasjon(fnr) // har adressebeskyttelse
        val isKanVarsles = dkifConsumer.person(fnr)?.kanVarsles // status i KRR: [reservert/ikke reservert + kontakt info nyere enn 18mnd]

        return UserAccessStatus(
            fnr,
            canUserBeDigitallyNotified(isKode6Eller7, isKanVarsles),
            canUserBePhysicallyNotified(isKode6Eller7, isKanVarsles),
        )
    }

    fun canUserBeNotifiedByEmailOrSMS(fnr: String) =
        getUserAccessStatus(fnr).canUserBeDigitallyNotified

    private fun canUserBeDigitallyNotified(isKode6Eller7: Boolean?, isKanVarsles: Boolean?): Boolean {
        return false == isKode6Eller7 && true == isKanVarsles
    }

    private fun canUserBePhysicallyNotified(isKode6Eller7: Boolean?, isKanVarsles: Boolean?): Boolean {
        return false == isKode6Eller7 && false == isKanVarsles
    }
}
