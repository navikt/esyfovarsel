package no.nav.syfo.consumer.narmesteLeder

import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.db.NarmesteLederService")
class NarmesteLederService(val narmesteLederConsumer: NarmesteLederConsumer) {
    fun getNarmesteLederRelasjon(fnr: String, orgnummer: String, uuid: String): NarmesteLederRelasjon? {
        val nlRelasjon = runBlocking { narmesteLederConsumer.getNarmesteLeder(fnr, orgnummer)?.narmesteLederRelasjon }
        if (nlRelasjon == null) {
            log.info("NarmesteLederService: Nærmeste leder relasjon er null for uuid: $uuid")
            return null
        }
        if (uuid == "66705dc4-e3b6-4afd-ab1a-ccd08bbcbc78") {
            val nlf = nlRelasjon.narmesteLederFnr
            val epost = nlRelasjon.narmesteLederEpost
            if (nlf == null) {
                log.info("NarmesteLederService: NL-fnr er null!")
            } else {
                log.info("NarmesteLederService: ${nlf.substring(0, 6)} lengde = ${nlf.length}")
            }
            if (epost == null) {
                log.info("NarmesteLederService: NL-epost er null!")
            } else {
                log.info("NarmesteLederService: epost lengde = ${epost.length}")
            }
        }
        return nlRelasjon
    }

    fun hasNarmesteLederInfo(narmesteLederRelasjon: NarmesteLederRelasjon?): Boolean {
        return (narmesteLederRelasjon !== null) && (narmesteLederRelasjon.narmesteLederFnr !== null) && (narmesteLederRelasjon.narmesteLederEpost !== null)
    }
}
