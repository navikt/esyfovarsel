package no.nav.syfo.consumer.syfosmregister.sykmeldingModel

import java.time.LocalDate

data class ErIkkeIArbeidDTO(
    val arbeidsforPaSikt: Boolean,
    val arbeidsforFOM: LocalDate?,
    val vurderingsdato: LocalDate?
)
