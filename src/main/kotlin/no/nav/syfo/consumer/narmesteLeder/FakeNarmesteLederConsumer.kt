package no.nav.syfo.consumer.narmesteLeder

class FakeNarmesteLederConsumer : INarmesteLederConsumer {
    override suspend fun getNarmesteLeder(
        ansattFnr: String,
        orgnummer: String,
    ): NarmestelederResponse =
        NarmestelederResponse(
            narmesteLederRelasjon =
                NarmesteLederRelasjon(
                    narmesteLederId = "local-narmeste-leder",
                    fnr = ansattFnr,
                    orgnummer = orgnummer,
                    narmesteLederFnr = ansattFnr.reversed(),
                    narmesteLederTelefonnummer = "00000000",
                    narmesteLederEpost = "narmeste.leder@example.invalid",
                    arbeidsgiverForskutterer = true,
                    skrivetilgang = true,
                    tilganger = Tilgang.entries.toList(),
                    navn = "Lokal Nærmeste Leder",
                ),
        )
}
