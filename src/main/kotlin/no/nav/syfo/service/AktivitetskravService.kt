package no.nav.syfo.service

import java.time.LocalDate
import java.util.*
import no.nav.syfo.domain.HendelseType.AKTIVITETSKRAV_VARSEL
import no.nav.syfo.domain.Sykeforloep
import no.nav.syfo.domain.Sykmelding
import no.nav.syfo.logger
import no.nav.syfo.util.finnAvstandTilnaermestePeriode
import no.nav.syfo.util.finnSykmeldingsgradPaGittDato
import no.nav.syfo.util.harPeriodeSomMatcherAvstandTilnaermestePeriode
import kotlin.Comparator

const val AKTIVITETSKRAV_DAGER: Long = 42

class AktivitetskravService(varselStatusService: VarselStatusService) {
    val LOGGER = logger()
    var varselStatusService = varselStatusService

    fun datoForAktivitetskravvarsel(sykmelding: Sykmelding, sykeforloep: Sykeforloep): Optional<LocalDate> {
        val aktivitetskravdato: LocalDate = finnAktivitetskravdato(sykeforloep)
        if (!erAktivitetskravdatoPassert(aktivitetskravdato)
                && er100prosentSykmeldtPaaDato(sykeforloep, aktivitetskravdato)
                && !erAlleredePlanlagt(sykmelding.bruker.aktoerId, sykeforloep.sykmeldinger)
                && !harAlleredeBlittSendt(sykmelding.bruker.aktoerId, sykeforloep.sykmeldinger)) {
            LOGGER.info("Planlegger aktivitetskravvarsel med dato {}", aktivitetskravdato)
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
            LOGGER.info("Planlegger ikke aktivitetskravvarsel: Ikke 100% sykmeldt på aktivitetskravdato!")
        }

        return er100ProsentSykmeldt
    }

    private fun erAktivitetskravdatoPassert(aktivitetskravDato: LocalDate): Boolean {
        val aktivitetskravdatoErPassert = aktivitetskravDato.isBefore(LocalDate.now())
        if (aktivitetskravdatoErPassert) {
            LOGGER.info("Planlegger ikke aktivitetskravvarsel: Aktivitetskravdato er passert!")
        }
        return aktivitetskravdatoErPassert
    }

    private fun finnAktivitetskravdato(sykeforloep: Sykeforloep): LocalDate {
        return sykeforloep.oppfolgingsdato.plusDays(AKTIVITETSKRAV_DAGER)
    }

    private fun harAlleredeBlittSendt(aktoerId: String, sykmeldinger: List<Sykmelding>): Boolean {
        return varselStatusService.harAlleredeBlittSendt(aktoerId, sykmeldinger, AKTIVITETSKRAV_VARSEL)
    }

    private fun erAlleredePlanlagt(aktoerId: String, sykmeldinger: List<Sykmelding>): Boolean {
        return varselStatusService.erAlleredePlanlagt(aktoerId, sykmeldinger, AKTIVITETSKRAV_VARSEL)
    }
}
