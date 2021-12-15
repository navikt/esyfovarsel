package no.nav.syfo.kafka.oppfolgingstilfelle.domain

import no.nav.syfo.consumer.domain.Syketilfelledag
import java.time.LocalDateTime

data class OppfolgingstilfellePerson(
    val aktorId: String,
    val tidslinje: List<Syketilfelledag>,
    val sisteDagIArbeidsgiverperiode: Syketilfelledag,
    val antallBrukteDager: Int,
    val oppbruktArbeidsgvierperiode: Boolean,
    val utsendelsestidspunkt: LocalDateTime
) {
    override fun toString(): String = "tidslinje: " + tidslinje.toString()
}
