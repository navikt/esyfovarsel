package no.nav.syfo.service

import no.nav.syfo.consumer.DkifConsumer
import no.nav.syfo.consumer.PdlConsumer

class AccessControl(val pdlConsumer: PdlConsumer, val dkifConsumer: DkifConsumer) {

    fun getFnrIfUserCanBeNotified(aktorId: String): String? {
        return if (pdlConsumer.isBrukerGradertForInformasjon(aktorId) == false && dkifConsumer.kontaktinfo(aktorId)?.kanVarsles == true)
            return pdlConsumer.getFnr(aktorId)
        else
            null
    }
}
