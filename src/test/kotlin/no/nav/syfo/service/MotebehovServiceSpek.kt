package no.nav.syfo.service

import no.nav.syfo.domain.*
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.util.*

object MotebehovServiceSpek : Spek({

    describe("MotebehovServiceSpek") {
        val motebehovService = MotebehovService()
        val SYKEFORLOEP_START_DAGER: Long = (SVAR_MOTEBEHOV_DAGER + 1)

        it("erSykmeldtPaaDato skal returnere true for 1 sykmelding med 1 periode") {
            // En periode ingen konflikt
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(
                            listOf(
                                    Sykmelding()
                                            .withPerioder(listOf(
                                                    Periode()
                                                            .withFom(now().minusDays(SYKEFORLOEP_START_DAGER))
                                                            .withTom(now().minusDays(0))
                                                            .withGrad(100)
                                            ))
                                            .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(SYKEFORLOEP_START_DAGER))
                            )
                    )
                    .withOppfolgingsdato(now().minusDays(SYKEFORLOEP_START_DAGER))

            val erSykmeldt: Boolean = motebehovService.erSykmeldtPaaDato(sykeforloep, sykeforloep.oppfolgingsdato.plusDays(SVAR_MOTEBEHOV_DAGER))

            erSykmeldt shouldEqual true
        }

        it("erSykmeldtPaaDato skal returnere true for 1 sykmelding med 2 perioder") {
            // To periode ingen konflikt - er 100% ved 112 dager
            val sykeforloep1 = Sykeforloep()
                    .withSykmeldinger(listOf(
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(now().minusDays(SYKEFORLOEP_START_DAGER))
                                                    .withTom(now().minusDays(25))
                                                    .withGrad(50),
                                            Periode()
                                                    .withFom(now().minusDays(24))
                                                    .withTom(now().minusDays(0))
                                                    .withGrad(100)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(SYKEFORLOEP_START_DAGER))
                    )).withOppfolgingsdato(now().minusDays(SYKEFORLOEP_START_DAGER))
            var erSykmeldt: Boolean = motebehovService.erSykmeldtPaaDato(sykeforloep1, sykeforloep1.oppfolgingsdato.plusDays(SVAR_MOTEBEHOV_DAGER))

            erSykmeldt shouldEqual true

            // To periode ingen konflikt - er 50% ved 42 dager
            val sykeforloep2 = Sykeforloep()
                    .withSykmeldinger(listOf(
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(now().minusDays(50))
                                                    .withTom(now().minusDays(25))
                                                    .withGrad(100),
                                            Periode()
                                                    .withFom(now().minusDays(24))
                                                    .withTom(now())
                                                    .withGrad(50)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(50))
                    ))
                    .withOppfolgingsdato(now().minusDays(50))
            erSykmeldt = motebehovService.erSykmeldtPaaDato(sykeforloep2, sykeforloep2.oppfolgingsdato.plusDays(SVAR_MOTEBEHOV_DAGER))

            erSykmeldt shouldEqual false
        }

        it("erSykmeldtPaaDato skal returnere true for flere sykmeldinger") {
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(now().minusDays(SYKEFORLOEP_START_DAGER))
                                                    .withTom(now())
                                                    .withGrad(100)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(SYKEFORLOEP_START_DAGER)),
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(now().minusDays(24))
                                                    .withTom(now())
                                                    .withGrad(50)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(SYKEFORLOEP_START_DAGER))
                    ))
                    .withOppfolgingsdato(now().minusDays(SYKEFORLOEP_START_DAGER))
            val erSykmeldt: Boolean = motebehovService.erSykmeldtPaaDato(sykeforloep, sykeforloep.oppfolgingsdato.plusDays(SVAR_MOTEBEHOV_DAGER))

            erSykmeldt shouldEqual true
        }

        it("erSykmeldtPaaDato skal returnere true for flere sykmeldinger med konflikt") {
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(now().minusDays(SYKEFORLOEP_START_DAGER))
                                                    .withTom(now().minusDays(20))
                                                    .withGrad(100),
                                            Periode()
                                                    .withFom(now().minusDays(19))
                                                    .withTom(now())
                                                    .withGrad(100)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(SYKEFORLOEP_START_DAGER))
                                    .withBehandletDato(LocalDateTime.now().minusDays(SYKEFORLOEP_START_DAGER)),
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(now().minusDays(19))
                                                    .withTom(now())
                                                    .withGrad(50)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(SYKEFORLOEP_START_DAGER))
                                    .withBehandletDato(LocalDateTime.now().minusDays(19)),
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(now().minusDays(24))
                                                    .withTom(now())
                                                    .withGrad(30)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(SYKEFORLOEP_START_DAGER))
                                    .withBehandletDato(LocalDateTime.now().minusDays(24))
                    ))
                    .withOppfolgingsdato(now().minusDays(SYKEFORLOEP_START_DAGER))
            val erSykmeldt: Boolean = motebehovService.erSykmeldtPaaDato(sykeforloep, sykeforloep.oppfolgingsdato.plusDays(SVAR_MOTEBEHOV_DAGER))
            // SykmeldingTestUtils.visualiser(sykeforloep.sykmeldinger)

            erSykmeldt shouldEqual true
        }

        it("erSykmeldtPaaDato skal returnere true for overlappende perioder innad i sykmelding") {
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(now().minusDays(SYKEFORLOEP_START_DAGER))
                                                    .withTom(now())
                                                    .withGrad(50),
                                            Periode()
                                                    .withFom(now().minusDays(24))
                                                    .withTom(now())
                                                    .withGrad(100)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(SYKEFORLOEP_START_DAGER))
                    ))
                    .withOppfolgingsdato(now().minusDays(SYKEFORLOEP_START_DAGER))
            val erSykmeldt: Boolean = motebehovService.erSykmeldtPaaDato(sykeforloep, sykeforloep.oppfolgingsdato.plusDays(SVAR_MOTEBEHOV_DAGER))
            // todo SykmeldingTestUtils.visualiser(sykeforloep.sykmeldinger)

            erSykmeldt shouldEqual true
        }

        it("muligVarselDato skal returnere empty om dato for SvarMotebehovVarsel er passert") {
            val sykmeldingDokument: Sykmelding = Sykmelding()
                    .withPerioder(listOf(
                            Periode()
                                    .withGrad(SVAR_MOTEBEHOV_DAGER.toInt() + 1)
                                    .withFom(now().minusDays(100))
                                    .withTom(now().minusDays(0)),
                            Periode()
                                    .withGrad(100)
                                    .withFom(now().plusDays(4))
                                    .withTom(now().plusDays(14))
                    ))
                    .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(SVAR_MOTEBEHOV_DAGER + 1L))
                    .withBruker(Bruker().withAktoerId("id"))
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(sykmeldingDokument))
                    .withOppfolgingsdato(now().minusDays(SVAR_MOTEBEHOV_DAGER + 1))
            val muligVarselDato: Optional<LocalDate> = motebehovService.datoForSvarMotebehov(sykmeldingDokument, sykeforloep)

            muligVarselDato shouldEqual Optional.empty()
        }

        it("muligVarselDato skal returnere empty om ingen aktiv sykmelding") {
            val sykmeldingDokument: Sykmelding = Sykmelding()
                    .withPerioder(listOf(
                            Periode()
                                    .withFom(now().minusDays(20))
                                    .withTom(now().minusDays(10)),
                            Periode()
                                    .withGrad(100)
                                    .withFom(now().plusDays(10))
                                    .withTom(now().plusDays(0))
                    ))
                    .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(10))
                    .withBehandletDato(LocalDateTime.now().minusDays(10))
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(sykmeldingDokument))
                    .withOppfolgingsdato(now().minusDays(10))
            val muligVarselDato: Optional<LocalDate> = motebehovService.datoForSvarMotebehov(sykmeldingDokument, sykeforloep)

            muligVarselDato shouldEqual Optional.empty()
        }

        it("muligVarselDato får dato dersom sykmelding bryter 112-dagersgrense og 100% ved dag 112") {
            val dagerSidenIdentdato = 10L
            val sykmeldingDokument: Sykmelding = Sykmelding()
                    .withPerioder(listOf(
                            Periode()
                                    .withGrad(80)
                                    .withFom(now().minusDays(dagerSidenIdentdato))
                                    .withTom(now().minusDays(0)),
                            Periode()
                                    .withGrad(100)
                                    .withFom(now().plusDays(4))
                                    .withTom(now().plusDays(SVAR_MOTEBEHOV_DAGER + 1))
                    ))
                    .withSyketilfelleStartDatoFraInfotrygd(now().minusDays(dagerSidenIdentdato))
                    .withBruker(Bruker().withAktoerId("id"))
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(sykmeldingDokument))
                    .withOppfolgingsdato(now().minusDays(dagerSidenIdentdato))
            val muligVarselDato: Optional<LocalDate> = motebehovService.datoForSvarMotebehov(sykmeldingDokument, sykeforloep)

            muligVarselDato.isPresent shouldEqual true
            muligVarselDato.get() shouldEqual now().plusDays(SVAR_MOTEBEHOV_DAGER - dagerSidenIdentdato)
        }

        it("muligVarselDato får ikke dato for gammelt forløp") {
            val sykmeldingDokument: Sykmelding = Sykmelding()
                    .withPerioder(listOf(
                            Periode()
                                    .withGrad(100)
                                    .withFom(LocalDate.of(2016, 5, 1))
                                    .withTom(LocalDate.of(2016, 6, 1)),
                            Periode()
                                    .withGrad(100)
                                    .withFom(LocalDate.of(2016, 6, 2))
                                    .withTom(LocalDate.of(2016, 7, 1))
                    ))
                    .withSyketilfelleStartDatoFraInfotrygd(LocalDate.of(2016, 5, 1))
                    .withBruker(Bruker().withAktoerId("id"))
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(sykmeldingDokument))
                    .withOppfolgingsdato(LocalDate.of(2016, 5, 1))
            val muligVarselDato: Optional<LocalDate> = motebehovService.datoForSvarMotebehov(sykmeldingDokument, sykeforloep)

            muligVarselDato.isPresent shouldEqual false
        }
    }
})
