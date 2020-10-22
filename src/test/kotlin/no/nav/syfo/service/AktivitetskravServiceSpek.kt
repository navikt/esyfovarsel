package no.nav.syfo.service

import no.nav.syfo.domain.Bruker
import no.nav.syfo.domain.Periode
import no.nav.syfo.domain.Sykeforloep
import no.nav.syfo.domain.Sykmelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

object AktivitetskravServiceSpek : Spek({

    describe("AktivitetskravServiceSpek") {
        val aktivitetskravService = AktivitetskravService()

        it("er100prosentSykmeldtPaaDato skal returnere true for 1 sykmelding med 1 periode") {
            val sykeforloep: Sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(Sykmelding()
                            .withPerioder(listOf(Periode()
                                    .withFom(LocalDate.now().minusDays(50))
                                    .withTom(LocalDate.now().minusDays(0))
                                    .withGrad(100)
                            ))
                            .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(50))
                    )).withOppfolgingsdato(LocalDate.now().minusDays(50))

            val er100prosentSykmeldt: Boolean = aktivitetskravService.er100prosentSykmeldtPaaDato(sykeforloep, sykeforloep.oppfolgingsdato.plusDays(AKTIVITETSKRAV_DAGER))

            er100prosentSykmeldt shouldEqual true
        }

        it("er100prosentSykmeldtPaaDato skal returnere true for 1 sykmelding med 2 perioder") {
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(Sykmelding()
                            .withPerioder(listOf(
                                    Periode()
                                            .withFom(LocalDate.now().minusDays(50))
                                            .withTom(LocalDate.now().minusDays(25))
                                            .withGrad(50),
                                    Periode()
                                            .withFom(LocalDate.now().minusDays(24))
                                            .withTom(LocalDate.now().minusDays(0))
                                            .withGrad(100)
                            ))
                            .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(50))
                    )).withOppfolgingsdato(LocalDate.now().minusDays(50))


            val er100prosentSykmeldt = aktivitetskravService.er100prosentSykmeldtPaaDato(sykeforloep, sykeforloep.oppfolgingsdato.plusDays(AKTIVITETSKRAV_DAGER))

            er100prosentSykmeldt shouldEqual true
        }

        it("er100prosentSykmeldtPaaDato skal returnere false for 1 sykmelding med 2 perioder") {
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(Sykmelding()
                            .withPerioder(listOf(
                                    Periode()
                                            .withFom(LocalDate.now().minusDays(50))
                                            .withTom(LocalDate.now().minusDays(25))
                                            .withGrad(100),
                                    Periode()
                                            .withFom(LocalDate.now().minusDays(24))
                                            .withTom(LocalDate.now().minusDays(0))
                                            .withGrad(50)
                            ))
                            .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(50))
                    )).withOppfolgingsdato(LocalDate.now().minusDays(50))

            val er100prosentSykmeldt = aktivitetskravService.er100prosentSykmeldtPaaDato(sykeforloep, sykeforloep.oppfolgingsdato.plusDays(AKTIVITETSKRAV_DAGER))

            er100prosentSykmeldt shouldEqual false
        }

        it("er100prosentSykmeldtPaaDato skal returnere false for 2 sykmeldinger med 1 periode") {
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(LocalDate.now().minusDays(50))
                                                    .withTom(LocalDate.now().minusDays(0))
                                                    .withGrad(100)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(50)),
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(LocalDate.now().minusDays(24))
                                                    .withTom(LocalDate.now().minusDays(0))
                                                    .withGrad(50)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(50))
                    )).withOppfolgingsdato(LocalDate.now().minusDays(50))

            val er100prosentSykmeldt = aktivitetskravService.er100prosentSykmeldtPaaDato(sykeforloep, sykeforloep.oppfolgingsdato.plusDays(AKTIVITETSKRAV_DAGER))

            er100prosentSykmeldt shouldEqual false
        }

        it("er100prosentSykmeldtPaaDato skal returnere false for flere sykmeldinger med konflikt") {
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(LocalDate.now().minusDays(50))
                                                    .withTom(LocalDate.now().minusDays(20))
                                                    .withGrad(100),
                                            Periode()
                                                    .withFom(LocalDate.now().minusDays(19))
                                                    .withTom(LocalDate.now().minusDays(5))
                                                    .withGrad(100)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(50))
                                    .withBehandletDato(LocalDateTime.now().minusDays(50)),
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(LocalDate.now().minusDays(19))
                                                    .withTom(LocalDate.now().minusDays(5))
                                                    .withGrad(50)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(50))
                                    .withBehandletDato(LocalDateTime.now().minusDays(19)),
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(LocalDate.now().minusDays(24))
                                                    .withTom(LocalDate.now().minusDays(5))
                                                    .withGrad(30)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(50))
                                    .withBehandletDato(LocalDateTime.now().minusDays(24))
                    )).withOppfolgingsdato(LocalDate.now().minusDays(50))

            val er100prosentSykmeldt = aktivitetskravService.er100prosentSykmeldtPaaDato(sykeforloep, sykeforloep.oppfolgingsdato.plusDays(AKTIVITETSKRAV_DAGER))
//            visualiser(sykeforloep.sykmeldinger)
            er100prosentSykmeldt shouldEqual false
        }

        it("er100prosentSykmeldtPaaDato skal returnere true for overlappende perioder innad i en sykmelding") {
            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(
                            Sykmelding()
                                    .withPerioder(listOf(
                                            Periode()
                                                    .withFom(LocalDate.now().minusDays(50))
                                                    .withTom(LocalDate.now().minusDays(0))
                                                    .withGrad(50),
                                            Periode()
                                                    .withFom(LocalDate.now().minusDays(24))
                                                    .withTom(LocalDate.now().minusDays(0))
                                                    .withGrad(100)
                                    ))
                                    .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(50))
                    )).withOppfolgingsdato(LocalDate.now().minusDays(50))

            val er100prosentSykmeldt = aktivitetskravService.er100prosentSykmeldtPaaDato(sykeforloep, sykeforloep.oppfolgingsdato.plusDays(AKTIVITETSKRAV_DAGER))
//            visualiser(sykeforloep.sykmeldinger)

            er100prosentSykmeldt shouldEqual true
        }

        it("datoForAktivitetskravvarsel skal returnere empty når dato aktivitetskravvarsel er passert") {
            val sykmelding: Sykmelding = Sykmelding()
                    .withPerioder(listOf(
                            Periode()
                                    .withGrad(100)
                                    .withFom(LocalDate.now().minusDays(100))
                                    .withTom(LocalDate.now().minusDays(0)),
                            Periode()
                                    .withGrad(100)
                                    .withFom(LocalDate.now().plusDays(4))
                                    .withTom(LocalDate.now().plusDays(14))
                    ))
                    .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(100))
                    .withBruker(Bruker().withAktoerId("id"))

            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(sykmelding))
                    .withOppfolgingsdato(LocalDate.now().minusDays(100))

            val muligVarselDato: Optional<LocalDate> = aktivitetskravService.datoForAktivitetskravvarsel(sykmelding, sykeforloep)

            muligVarselDato shouldEqual Optional.empty()
        }

        it("datoForAktivitetskravvarsel skal returnere empty om sykefravær inklusiv ny sykmelding er kortere enn 42 dager") {
            val sykmelding: Sykmelding = Sykmelding()
                    .withPerioder(listOf(
                            Periode()
                                    .withFom(LocalDate.now().minusDays(10))
                                    .withTom(LocalDate.now().minusDays(0)),
                            Periode()
                                    .withGrad(100)
                                    .withFom(LocalDate.now().plusDays(4))
                                    .withTom(LocalDate.now().plusDays(14))
                    ))
                    .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(10))
                    .withBehandletDato(LocalDateTime.now().minusDays(10))

            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(sykmelding))
                    .withOppfolgingsdato(LocalDate.now().minusDays(10))

            val muligVarselDato = aktivitetskravService.datoForAktivitetskravvarsel(sykmelding, sykeforloep)

            muligVarselDato shouldEqual Optional.empty()
        }

        it("datoForAktivitetskravvarsel skal returnere empty om ny sykmelding går på 42 dager men ikke 100 gradering") {
            val sykmelding: Sykmelding = Sykmelding()
                    .withPerioder(listOf(
                            Periode()
                                    .withFom(LocalDate.now().minusDays(10))
                                    .withTom(LocalDate.now().minusDays(0)),
                            Periode()
                                    .withGrad(80)
                                    .withFom(LocalDate.now().plusDays(4))
                                    .withTom(LocalDate.now().plusDays(55))
                    ))
                    .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(10))

            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(sykmelding))
                    .withOppfolgingsdato(LocalDate.now().minusDays(10))

            val muligVarselDato = aktivitetskravService.datoForAktivitetskravvarsel(sykmelding, sykeforloep)

            muligVarselDato shouldEqual Optional.empty()
        }

        it("datoForAktivitetskravvarsel skal returnere dato dersom sykmelding bryter 42 dagersgrense og 100 ved dag 42") {
            val dagerSidenIdentdato = 10
            val sykmelding: Sykmelding = Sykmelding()
                    .withPerioder(listOf(
                            Periode()
                                    .withGrad(80)
                                    .withFom(LocalDate.now().minusDays(dagerSidenIdentdato.toLong()))
                                    .withTom(LocalDate.now().minusDays(0)),
                            Periode()
                                    .withGrad(100)
                                    .withFom(LocalDate.now().plusDays(4))
                                    .withTom(LocalDate.now().plusDays(55))
                    ))
                    .withSyketilfelleStartDatoFraInfotrygd(LocalDate.now().minusDays(dagerSidenIdentdato.toLong()))
                    .withBruker(Bruker().withAktoerId("id"))

            val sykeforloep = Sykeforloep()
                    .withSykmeldinger(listOf(sykmelding))
                    .withOppfolgingsdato(LocalDate.now().minusDays(dagerSidenIdentdato.toLong()))

            val muligVarselDato = aktivitetskravService.datoForAktivitetskravvarsel(sykmelding, sykeforloep)

            muligVarselDato shouldEqual Optional.of(LocalDate.now().plusDays(42 - dagerSidenIdentdato.toLong()))
        }

        it("datoForAktivitetskravvarsel skal returnere empty for gammelt forløp") {
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

            val muligVarselDato = aktivitetskravService.datoForAktivitetskravvarsel(sykmeldingDokument, sykeforloep)

            muligVarselDato shouldEqual Optional.empty()
        }
    }
})
