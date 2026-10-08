package no.nav.syfo.consumer.narmesteLeder

class FakeNarmesteLederConsumer : INarmesteLederConsumer {
    override suspend fun getNarmesteLeder(
        ansattFnr: String,
        orgnummer: String,
    ): NarmesteLederRelasjon =
        NarmesteLederRelasjon(
            narmesteLederId = "local-narmeste-leder",
            narmesteLederFnr = ansattFnr.reversed(),
            narmesteLederEpost = "narmeste.leder@example.invalid",
        )
}
