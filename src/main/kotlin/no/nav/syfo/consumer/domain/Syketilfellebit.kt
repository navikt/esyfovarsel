package no.nav.syfo.consumer.domain

import java.time.LocalDateTime

enum class SyketilfellebitTag {
    SYKMELDING,
    SENDT,
    PAPIRSYKMELDING,
}

data class Syketilfellebit(
    val id: String? = null,
    val aktorId: String,
    val orgnummer: String? = null,
    val opprettet: LocalDateTime,
    val inntruffet: LocalDateTime,
    val tags: List<String>,
    val ressursId: String,
    val fom: LocalDateTime,
    val tom: LocalDateTime
)

