package no.nav.syfo.service

import no.nav.syfo.consumer.DkifConsumer
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.domain.kanVarsle
import org.slf4j.LoggerFactory

class AccessControl(val pdlConsumer: PdlConsumer, val dkifConsumer: DkifConsumer) {

    val log = LoggerFactory.getLogger("no.nav.syfo.service.AccessControl")

    fun getFnrIfUserCanBeNotified(aktorId: String): String? {
        return if (pdlConsumer.isBrukerGradertForInformasjon(aktorId) == false && dkifConsumer.kontaktinfo(aktorId)?.kanVarsle() == true)
            pdlConsumer.getFnr(aktorId)
        else
            null
    }
}
