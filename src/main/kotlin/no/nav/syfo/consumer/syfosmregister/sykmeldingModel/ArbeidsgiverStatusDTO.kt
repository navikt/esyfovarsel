package no.nav.syfo.consumer.syfosmregister.sykmeldingModel

data class ArbeidsgiverStatusDTO(
    val orgnummer: String,
    val juridiskOrgnummer: String? = null,
    val orgNavn: String,
)
