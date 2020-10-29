package no.nav.syfo.util

import no.nav.syfo.domain.Periode
import no.nav.syfo.domain.Sykmelding
import java.time.LocalDate
import java.util.*
import java.util.stream.Collectors

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

fun hentSenesteTOM(sykmeldingDokument: Sykmelding): LocalDate {
    val nyestePeriodeFoerst: List<Periode> = nyestePeriodeFoerst(sykmeldingDokument.perioder)
    return hentNyestePeriode(nyestePeriodeFoerst).tom
}

fun hentNyestePeriode(perioder: List<Periode>): Periode {
    return nyestePeriodeFoerst(perioder)
            .stream()
            .findFirst().get()
}

fun nyestePeriodeFoerst(perioder: List<Periode>): List<Periode> {
    return perioder.stream()
            .sorted(nyestePeriodeFoerst())
            .collect(Collectors.toList())
}

private fun nyestePeriodeFoerst(): Comparator<Periode>? {
    return Comparator { p1: Periode, p2: Periode ->
        val i = p2.fom.compareTo(p1.fom)
        if (i == 0) p2.tom.compareTo(p1.tom) else i
    }
}

fun hentTidligsteFOM(sykmeldingDokument: Sykmelding): LocalDate {
    return hentTidligsteFOMFraPerioder(sykmeldingDokument.perioder)
}

fun hentTidligsteFOMFraPerioder(perioder: List<Periode>): LocalDate {
    return eldstePeriodeFOM(perioder)
}

fun eldstePeriodeFOM(perioder: List<Periode>): LocalDate {
    return perioder.stream().sorted(eldstePeriodeFoerst()).findFirst().orElseThrow { RuntimeException("Mangler periode!") }.fom
}

private fun eldstePeriodeFoerst(): Comparator<Periode> {
    return Comparator.comparing { p: Periode -> p.fom }.thenComparing { p: Periode -> p.tom }
}