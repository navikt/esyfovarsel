package no.nav.syfo.kafka.producers.dinesykmeldte.domain

import java.time.OffsetDateTime

data class DineSykmeldteVarsel(
    val ansattFnr: String,
    val orgnr: String,
    val oppgavetype: String,
    val lenke: String?,
    val tekst: String,
    val utlopstidspunkt: OffsetDateTime? = null
)
