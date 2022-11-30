package no.nav.syfo.syketilfelle.domain

import java.time.LocalDate
import no.nav.syfo.kafka.consumers.syketilfelle.domain.Syketilfelledag
import no.nav.syfo.syketilfelle.AntallDagerIArbeidsgiverPeriode
import no.nav.syfo.syketilfelle.erArbeidsdag
import no.nav.syfo.syketilfelle.erFeriedag
import no.nav.syfo.syketilfelle.erSendt

class Oppfolgingstilfelle(
    val tidslinje: List<Syketilfelledag>,
    val sisteDagIArbeidsgiverperiode: Syketilfelledag,
    val dagerAvArbeidsgiverperiode: Int,
    val behandlingsdager: Int,
    val sisteSykedagEllerFeriedag: LocalDate?
) {
    fun antallSykedager() = tidslinje.count { syketilfelledag ->
        syketilfelledag.erSendt() && !syketilfelledag.erArbeidsdag() && !syketilfelledag.erFeriedag()
    }

    fun oppbruktArbeidsgiverperiode() = dagerAvArbeidsgiverperiode > 16 || behandlingsdager > 12

    fun antallDagerAGPeriodeBrukt(): Int {
        return if (dagerAvArbeidsgiverperiode < AntallDagerIArbeidsgiverPeriode) dagerAvArbeidsgiverperiode else AntallDagerIArbeidsgiverPeriode
    }
}
