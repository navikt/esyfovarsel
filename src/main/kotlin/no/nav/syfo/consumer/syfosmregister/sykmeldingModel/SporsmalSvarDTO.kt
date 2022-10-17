package no.nav.syfo.consumer.syfosmregister.sykmeldingModel

data class SporsmalSvarDTO(
    val sporsmal: String?,
    val svar: String,
    val restriksjoner: List<SvarRestriksjonDTO>
)
