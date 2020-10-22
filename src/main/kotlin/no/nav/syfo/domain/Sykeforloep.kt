package no.nav.syfo.domain

import java.time.LocalDate

class Sykeforloep() {
    var oppfolgingsdato: LocalDate = LocalDate.now()
    var sykmeldinger: List<Sykmelding> = emptyList()

    fun withSykmeldinger(sykmeldinger: List<Sykmelding>): Sykeforloep {
        this.sykmeldinger = sykmeldinger
        return this
    }

    fun withOppfolgingsdato(dato: LocalDate): Sykeforloep {
        this.oppfolgingsdato = dato
        return this
    }

    fun hentSykmeldingerGittDato(dato: LocalDate): List<Sykmelding> {
        return sykmeldinger
                .filter { s: Sykmelding -> s.periodeVedGittDato(dato).isPresent() }
    }
}