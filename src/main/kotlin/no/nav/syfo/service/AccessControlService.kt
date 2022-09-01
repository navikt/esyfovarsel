package no.nav.syfo.service

import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.dkif.DkifConsumer
import org.slf4j.LoggerFactory

class AccessControlService(val pdlConsumer: PdlConsumer, val dkifConsumer: DkifConsumer) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.service.AccessControlService")
    fun getUserAccessStatusByAktorId(aktorId: String): UserAccessStatus {
        val isKode6Eller7 = pdlConsumer.isBrukerGradertForInformasjon(aktorId) // har adressebeskyttelse
        val isKanVarsles = dkifConsumer.kontaktinfo(aktorId)?.kanVarsles // status i KRR: [reservert/ikke reservert + kontakt info nyere enn 18mnd]

        return UserAccessStatus(
            pdlConsumer.getFnr(aktorId),
            canUserBeDigitallyNotified(isKode6Eller7, isKanVarsles),
            canUserBePhysicallyNotified(isKode6Eller7, isKanVarsles),
        )
    }

    fun getUserAccessStatusByFnr(fnr: String): UserAccessStatus {
        val isKode6Eller7 = pdlConsumer.isBrukerGradertForInformasjon(fnr) // har adressebeskyttelse
        val isKanVarsles = dkifConsumer.person(fnr)?.kanVarsles // status i KRR: [reservert/ikke reservert + kontakt info nyere enn 18mnd]

        return UserAccessStatus(
            fnr,
            canUserBeDigitallyNotified(isKode6Eller7, isKanVarsles),
            canUserBePhysicallyNotified(isKode6Eller7, isKanVarsles),
        )
    }

    private fun canUserBeDigitallyNotified(isKode6Eller7: Boolean?, isKanVarsles: Boolean?): Boolean {
        log.info("isKode6Eller7: $isKode6Eller7")
        log.info("isKanVarsles: $isKanVarsles")

        return false == isKode6Eller7 && true == isKanVarsles
    }

    private fun canUserBePhysicallyNotified(isKode6Eller7: Boolean?, isKanVarsles: Boolean?): Boolean {
        return false == isKode6Eller7 && false == isKanVarsles
    }
}
