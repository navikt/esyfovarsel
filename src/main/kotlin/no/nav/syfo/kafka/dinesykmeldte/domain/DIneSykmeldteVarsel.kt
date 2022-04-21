package no.nav.syfo.kafka.dinesykmeldte.domain

import java.time.OffsetDateTime

data class DineSykmeldteVarsel(
    val ansattFnr: String,
    val orgnr: String,
    val oppgavetype: String,
    val lenke: String?,
    val tekst: String,
    val utlopstidspunkt: OffsetDateTime? = OffsetDateTime.now().plusMinutes(15)
)