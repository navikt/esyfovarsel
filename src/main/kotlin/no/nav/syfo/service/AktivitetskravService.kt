package no.nav.syfo.service

import no.nav.syfo.domain.Sykeforloep
import no.nav.syfo.domain.Sykmelding
import no.nav.syfo.util.finnAvstandTilnaermestePeriode
import no.nav.syfo.util.finnSykmeldingsgradPaGittDato
import no.nav.syfo.util.harPeriodeSomMatcherAvstandTilnaermestePeriode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

const val AKTIVITETSKRAV_DAGER: Long = 42

class AktivitetskravService {
    val log: Logger = LoggerFactory.getLogger(this::class.simpleName)

    fun datoForAktivitetskravvarsel(sykmelding: Sykmelding, sykeforloep: Sykeforloep): Optional<LocalDate> {
        val aktivitetskravdato: LocalDate = finnAktivitetskravdato(sykeforloep)
        if (!erAktivitetskravdatoPassert(aktivitetskravdato)
                && er100prosentSykmeldtPaaDato(sykeforloep, aktivitetskravdato)
                && !erAlleredePlanlagt(sykmelding.bruker.aktoerId, sykeforloep)
                && !harAlleredeBlittSendt(sykmelding.bruker.aktoerId, sykeforloep)) {
            log.info("Planlegger aktivitetskravvarsel med dato {}", aktivitetskravdato)
            return Optional.of(aktivitetskravdato)
        }
        return Optional.empty()
    }

    fun er100prosentSykmeldtPaaDato(sykeforloep: Sykeforloep, aktivitetskravdato: LocalDate): Boolean {
        val aktiveSykmeldingerVedAktivitetskrav: List<Sykmelding> = sykeforloep.hentSykmeldingerGittDato(aktivitetskravdato)
        val avstandTilnaermestePeriode: Int = finnAvstandTilnaermestePeriode(aktivitetskravdato, aktiveSykmeldingerVedAktivitetskrav)
        val nyesteBehandlingsdatoFoerst: Comparator<Sykmelding> = Comparator { sd1: Sykmelding, sd2: Sykmelding -> sd2.behandletDato.compareTo(sd1.behandletDato) }

        val sykmelding: Sykmelding? = aktiveSykmeldingerVedAktivitetskrav
                .filter { sykmelding -> harPeriodeSomMatcherAvstandTilnaermestePeriode(sykmelding, aktivitetskravdato, avstandTilnaermestePeriode) }
                .sortedWith(nyesteBehandlingsdatoFoerst)
                .firstOrNull()

        val er100ProsentSykmeldt = 100 == finnSykmeldingsgradPaGittDato(aktivitetskravdato, sykmelding?.perioder)

        if (!er100ProsentSykmeldt) {
            log.info("Planlegger ikke aktivitetskravvarsel: Ikke 100% sykmeldt på aktivitetskravdato!")
        }

        return er100ProsentSykmeldt
    }

    private fun erAktivitetskravdatoPassert(aktivitetskravDato: LocalDate): Boolean {
        val aktivitetskravdatoErPassert = aktivitetskravDato.isBefore(LocalDate.now())
        if (aktivitetskravdatoErPassert) {
            log.info("Planlegger ikke aktivitetskravvarsel: Aktivitetskravdato er passert!")
        }
        return aktivitetskravdatoErPassert
    }

    private fun finnAktivitetskravdato(sykeforloep: Sykeforloep): LocalDate {
        return sykeforloep.oppfolgingsdato.plusDays(AKTIVITETSKRAV_DAGER)
    }

    private fun erAlleredePlanlagt(aktoerId: String, sykeforloep: Sykeforloep): Boolean {
        return false;
    }

    private fun harAlleredeBlittSendt(aktoerId: String, sykeforloep: Sykeforloep): Boolean {
        return false;
    }
}
