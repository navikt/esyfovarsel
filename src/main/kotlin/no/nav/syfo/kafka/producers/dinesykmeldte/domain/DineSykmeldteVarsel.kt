package no.nav.syfo.kafka.producers.dinesykmeldte.domain

import java.time.OffsetDateTime
import java.util.UUID

data class DineSykmeldteVarsel(
    val id: UUID = UUID.randomUUID(),
    val ansattFnr: String,
    val orgnr: String,
    val oppgavetype: String,
    val lenke: String?,
    val tekst: String,
    val utlopstidspunkt: OffsetDateTime? = null,
)
