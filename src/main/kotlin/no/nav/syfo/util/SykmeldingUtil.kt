package no.nav.syfo.util

import no.nav.syfo.domain.Periode
import no.nav.syfo.domain.Sykmelding
import java.time.LocalDate
import java.util.*

fun finnSykmeldingsgradPaGittDato(aktivitetskravdato: LocalDate, perioder: List<Periode>?): Int {
    return if (perioder.isNullOrEmpty()) 0 else perioder.filter { periode -> erDatoIPerioden(aktivitetskravdato, periode.fom, periode.tom) }
            .sortedWith(Comparator.comparingInt { periode -> antallDagerMellom(periode.fom, aktivitetskravdato) })
            .first().grad
}

fun finnAvstandTilnaermestePeriode(aktivitetskravdato: LocalDate, sykmeldinger: List<Sykmelding>): Int {
    return sykmeldinger
            .flatMap { sykmelding -> sykmelding.perioder }
            .map { periode -> antallDager(periode.fom, aktivitetskravdato) }
            .min() ?: 0
}

fun harPeriodeSomMatcherAvstandTilnaermestePeriode(sykmelding: Sykmelding, aktivitetskravdato: LocalDate, avstandTilnaermestePeriode: Int): Boolean {
    return sykmelding.perioder
            .map { periode -> antallDager(periode.fom, aktivitetskravdato) }
            .any { avstand -> avstand == avstandTilnaermestePeriode }
}
