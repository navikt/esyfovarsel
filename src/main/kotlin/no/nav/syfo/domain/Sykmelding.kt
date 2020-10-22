package no.nav.syfo.domain

import no.nav.syfo.util.erDatoIPerioden
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.function.Predicate

class Sykmelding() {
    var perioder: List<Periode> = emptyList()
    var behandletDato: LocalDateTime = LocalDateTime.now()
    var bruker: Bruker = Bruker()
    var syketilfelleStartDatoFraInfotrygd: LocalDate = LocalDate.now()
    var id: Long? = null
    var meldingId: String? = null

    fun withPerioder(perioder: List<Periode>): Sykmelding {
        this.perioder = perioder
        return this
    }

    fun withSyketilfelleStartDatoFraInfotrygd(syketilfelleStartDatoFraInfotrygd: LocalDate): Sykmelding {
        this.syketilfelleStartDatoFraInfotrygd = syketilfelleStartDatoFraInfotrygd
        return this
    }

    fun withBehandletDato(behandletDato: LocalDateTime): Sykmelding {
        this.behandletDato = behandletDato
        return this
    }

    fun withBruker(bruker: Bruker): Sykmelding {
        this.bruker = bruker
        return this
    }

    fun periodeVedGittDato(dato: LocalDate): Optional<Periode> {
        return perioder.stream().filter(Predicate<Periode> { p: Periode -> erDatoIPerioden(dato, p.fom, p.tom) }).findFirst()
    }

    fun withId(id: Long): Sykmelding {
        this.id = id
        return this
    }

    fun withMeldingId(meldingId: String): Sykmelding {
        this.meldingId = meldingId
        return this
    }
}