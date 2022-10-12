package no.nav.syfo.consumer.syfosmregister.sykmeldingModel

import java.time.LocalDate

data class KontaktMedPasientDTO(
    val kontaktDato: LocalDate?,
    val begrunnelseIkkeKontakt: String?
)
