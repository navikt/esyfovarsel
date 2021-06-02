package no.nav.syfo.consumer.domain

import java.time.OffsetDateTime

data class SykmeldingStatus (
    var statusEvent: String? = null,
    var timestamp: OffsetDateTime? = null,
    var arbeidsgiver: ArbeidsgiverStatus? = null
)
