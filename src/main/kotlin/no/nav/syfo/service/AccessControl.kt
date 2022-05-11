package no.nav.syfo.service

import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.dkif.DkifConsumer
import org.slf4j.LoggerFactory

class AccessControl(val pdlConsumer: PdlConsumer, val dkifConsumer: DkifConsumer) {

    val log = LoggerFactory.getLogger("no.nav.syfo.service.AccessControl")

    fun getFnrIfUserCanBeNotified(aktorId: String): String? {
        log.info("pdlConsumer.isBrukerGradertForInformasjon(aktorId): ${pdlConsumer.isBrukerGradertForInformasjon(aktorId)}") // TODO Delete
        log.info("dkifConsumer.kontaktinfo(aktorId)?.kanVarsles: ${dkifConsumer.kontaktinfo(aktorId)?.kanVarsles}") // TODO Delete
        return if (pdlConsumer.isBrukerGradertForInformasjon(aktorId) == false && dkifConsumer.kontaktinfo(aktorId)?.kanVarsles == true)
            pdlConsumer.getFnr(aktorId)
        else
            null
    }
}
