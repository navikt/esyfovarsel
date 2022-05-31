package no.nav.syfo.consumer.narmesteLeder

import kotlinx.coroutines.runBlocking

class NarmesteLederService(val narmesteLederConsumer: NarmesteLederConsumer) {
    fun getNarmesteLederRelasjon(fnr: String, orgnummer: String): NarmesteLederRelasjon? {
        return runBlocking { narmesteLederConsumer.getNarmesteLeder(fnr, orgnummer)?.narmesteLederRelasjon }
    }

    fun hasNarmesteLederInfo(narmesteLederRelasjon: NarmesteLederRelasjon?): Boolean {
        return (narmesteLederRelasjon !== null) && (narmesteLederRelasjon.narmesteLederFnr !== null) && (narmesteLederRelasjon.narmesteLederEpost !== null)
    }
}
