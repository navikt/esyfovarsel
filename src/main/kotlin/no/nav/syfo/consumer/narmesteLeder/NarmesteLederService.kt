package no.nav.syfo.consumer.narmesteLeder

import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.db.NarmesteLederService")
class NarmesteLederService(val narmesteLederConsumer: NarmesteLederConsumer) {
    suspend fun getNarmesteLederRelasjon(fnr: String, orgnummer: String, uuid: String): NarmesteLederRelasjon? {
        return narmesteLederConsumer.getNarmesteLeder(fnr, orgnummer)?.narmesteLederRelasjon
    }

    fun hasNarmesteLederInfo(narmesteLederRelasjon: NarmesteLederRelasjon?): Boolean {
        return (narmesteLederRelasjon !== null) && (narmesteLederRelasjon.narmesteLederFnr !== null) && (narmesteLederRelasjon.narmesteLederEpost !== null)
    }
}
