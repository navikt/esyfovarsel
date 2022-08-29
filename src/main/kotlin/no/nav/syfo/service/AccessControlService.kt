package no.nav.syfo.service

import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.dkif.DkifConsumer

class AccessControlService(val pdlConsumer: PdlConsumer, val dkifConsumer: DkifConsumer) {

    fun getUserAccessStatusByAktorId(aktorId: String): UserAccessStatus {
        val isKode6Eller7 = pdlConsumer.isBrukerGradertForInformasjon(aktorId)
        val isKanVarsles = dkifConsumer.kontaktinfo(aktorId)?.kanVarsles

        val canUserBePhysicallyNotified = (isKode6Eller7 == false && isKanVarsles == false)
        val canUserBeDigitallyNotified = (isKode6Eller7 == false && isKanVarsles == true)

        return UserAccessStatus(pdlConsumer.getFnr(aktorId), canUserBeDigitallyNotified, canUserBePhysicallyNotified, isKode6Eller7, isKanVarsles)
    }

    fun getUserAccessStatusByFnr(fnr: String): UserAccessStatus {
        val isKode6Eller7 = pdlConsumer.isBrukerGradertForInformasjon(fnr)
        val isKanVarsles = dkifConsumer.person(fnr)?.kanVarsles

        val canUserBePhysicallyNotified = (isKode6Eller7 == false && isKanVarsles == false)
        val canUserBeDigitallyNotified = (isKode6Eller7 == false && isKanVarsles == true)

        return UserAccessStatus(fnr, canUserBeDigitallyNotified, canUserBePhysicallyNotified, isKode6Eller7, isKanVarsles)
    }
}
