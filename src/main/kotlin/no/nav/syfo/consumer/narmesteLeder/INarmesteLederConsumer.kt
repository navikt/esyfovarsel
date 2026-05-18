package no.nav.syfo.consumer.narmesteLeder

interface INarmesteLederConsumer {
    suspend fun getNarmesteLeder(
        ansattFnr: String,
        orgnummer: String,
    ): NarmestelederResponse?
}
