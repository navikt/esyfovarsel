package no.nav.syfo.service

import no.nav.syfo.consumer.DkifConsumer
import no.nav.syfo.consumer.PdlConsumer
import org.slf4j.LoggerFactory
import java.util.logging.Logger

class AccessControl(val pdlConsumer: PdlConsumer, val dkifConsumer: DkifConsumer) {

    val log = LoggerFactory.getLogger("no.nav.syfo.service.AccessControl")

    fun getFnrIfUserCanBeNotified(aktorId: String): String? {
        val gradert = pdlConsumer.isBrukerGradert(aktorId)
        log.info("DEBUG GRAD: $gradert")
        val reservert = dkifConsumer.kontaktinfo(aktorId)?.kanVarsles
        log.info("DEBUG DKIF: $reservert")
        val fnr = pdlConsumer.getFnr(aktorId)
        log.info("DEBUG FNR: $fnr")

        return if (pdlConsumer.isBrukerGradert(aktorId) == false && dkifConsumer.kontaktinfo(aktorId)?.kanVarsles == true)
            return pdlConsumer.getFnr(aktorId)
        else
            null
    }
}
