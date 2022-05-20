package no.nav.syfo.service

import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.dkif.DkifConsumer
import org.slf4j.LoggerFactory

class AccessControl(val pdlConsumer: PdlConsumer, val dkifConsumer: DkifConsumer) {

    val log = LoggerFactory.getLogger("no.nav.syfo.service.AccessControl")

    fun getFnrIfUserCanBeNotified(aktorId: String): String? {
        return if (pdlConsumer.isBrukerGradertForInformasjon(aktorId) == false && dkifConsumer.kontaktinfo(aktorId)?.kanVarsles == true)
            pdlConsumer.getFnr(aktorId)
        else
            null
    }

    fun canUserBeNotified(fnr: String): Boolean {
        return (pdlConsumer.isBrukerGradertForInformasjon(fnr) == false && dkifConsumer.person(fnr)?.kanVarsles == true)
    }
}
