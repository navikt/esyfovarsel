package no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain

import java.time.LocalDate
import java.time.OffsetDateTime

data class KSyketilfellebit (
    val id: String,
    val fnr: String,
    val orgnummer: String?,
    val opprettet: OffsetDateTime,
    val inntruffet: OffsetDateTime,
    val tags: Set<String>,
    val ressursId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val korrigererSendtSoknad: String?,
)
