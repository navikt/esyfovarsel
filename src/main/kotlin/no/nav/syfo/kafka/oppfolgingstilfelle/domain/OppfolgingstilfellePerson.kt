package no.nav.syfo.kafka.oppfolgingstilfelle.domain

import java.time.LocalDateTime

data class OppfolgingstilfellePerson(
    val aktorId: String,
    val tidslinje: List<Syketilfelledag>,
    val sisteDagIArbeidsgiverperiode: Syketilfelledag,
    val antallBrukteDager: Int,
    val oppbruktArbeidsgvierperiode: Boolean,
    val utsendelsestidspunkt: LocalDateTime
)
