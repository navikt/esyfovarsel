package no.nav.syfo.consumer.narmesteLeder

class NarmesteLederService(val narmesteLederConsumer: NarmesteLederConsumer) {
    suspend fun getNarmesteLederRelasjon(fnr: String, orgnummer: String, uuid: String): NarmesteLederRelasjon? {
        return narmesteLederConsumer.getNarmesteLeder(fnr, orgnummer)?.narmesteLederRelasjon
    }

    fun hasNarmesteLederInfo(narmesteLederRelasjon: NarmesteLederRelasjon?): Boolean {
        return (narmesteLederRelasjon !== null) && (narmesteLederRelasjon.narmesteLederFnr !== null) && (narmesteLederRelasjon.narmesteLederEpost !== null)
    }
}
