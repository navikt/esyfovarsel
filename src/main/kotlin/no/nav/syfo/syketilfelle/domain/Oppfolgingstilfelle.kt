package no.nav.syfo.syketilfelle.domain

import no.nav.syfo.syketilfelle.AntallDagerIArbeidsgiverPeriode
import no.nav.syfo.syketilfelle.domain.Tag.FERIE
import no.nav.syfo.syketilfelle.domain.Tag.SYKEPENGESOKNAD
import no.nav.syfo.syketilfelle.erArbeidsdag
import no.nav.syfo.syketilfelle.erFeriedag
import no.nav.syfo.syketilfelle.erSendt
import java.time.LocalDate

class Oppfolgingstilfelle(
    val tidslinje: List<Syketilfelledag>,
    val sisteDagIArbeidsgiverperiode: Syketilfelledag,
    val dagerAvArbeidsgiverperiode: Int,
    val behandlingsdager: Int,
    val sisteSykedagEllerFeriedag: LocalDate?
) {

    fun antallDager() = tidslinje.count { syketilfelledag ->
        (syketilfelledag.prioritertSyketilfellebit?.tags?.containsAll(listOf(SYKEPENGESOKNAD, FERIE))?.not())
            ?: true
    }

    fun antallSykedager() = tidslinje.count { syketilfelledag ->
        syketilfelledag.erSendt() && !syketilfelledag.erArbeidsdag() && !syketilfelledag.erFeriedag()
    }

    fun oppbruktArbeidsgvierperiode() = dagerAvArbeidsgiverperiode > 16 || behandlingsdager > 12

    fun arbeidsgiverperiode(): Pair<LocalDate, LocalDate> = forsteDagITilfellet() to sisteDagIArbeidsgiverperiode.dag

    fun antallDagerAGPeriodeBrukt(): Int {
        return if (dagerAvArbeidsgiverperiode < AntallDagerIArbeidsgiverPeriode) dagerAvArbeidsgiverperiode else AntallDagerIArbeidsgiverPeriode
    }

    private fun forsteDagITilfellet(): LocalDate = tidslinje.first().dag
}
