package no.nav.syfo.consumer.narmesteLeder

class NarmesteLederService(
    val narmesteLederConsumer: INarmesteLederConsumer,
) {
    suspend fun getNarmesteLederRelasjon(
        fnr: String,
        orgnummer: String,
    ): NarmesteLederRelasjon? = narmesteLederConsumer.getNarmesteLeder(fnr, orgnummer)?.narmesteLederRelasjon

    fun hasNarmesteLederInfo(narmesteLederRelasjon: NarmesteLederRelasjon?): Boolean =
        (narmesteLederRelasjon !== null) &&
            (narmesteLederRelasjon.narmesteLederFnr !== null) &&
            (narmesteLederRelasjon.narmesteLederEpost !== null)
}
