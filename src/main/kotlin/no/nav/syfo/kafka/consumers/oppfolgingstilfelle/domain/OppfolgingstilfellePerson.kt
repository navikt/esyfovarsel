package no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain

import java.time.LocalDateTime

data class OppfolgingstilfellePerson(
    val aktorId: String,
    val tidslinje: List<Syketilfelledag>,
    val sisteDagIArbeidsgiverperiode: Syketilfelledag,
    val antallBrukteDager: Int,
    val oppbruktArbeidsgiverperiode: Boolean,
    val utsendelsestidspunkt: LocalDateTime
)
