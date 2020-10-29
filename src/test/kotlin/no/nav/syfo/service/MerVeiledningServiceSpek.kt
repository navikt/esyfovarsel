package no.nav.syfo.service

import no.nav.syfo.domain.Sykmelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object MerVeiledningServiceSpek : Spek({

    val FOM = LocalDate.of(2018, 1, 1)
    val TOM = LocalDate.of(2018, 1, 20)

    describe("MerVeiledningServiceSpek") {
        val merVeiledningService = MerVeiledningService()

        fun setup() {
/*            Mockito.`when`<String>(sykmeldingDokument.getPasientFnr()).thenReturn("fnr")
            Mockito.`when`<String>(aktoerIdConsumer.finnAktoerId("fnr")).thenReturn("aktorId")
            sykmeldingDokument.perioder = listOf(
                    Periode()
                            .withFom(MerVeiledningServiceTest.FOM)
                            .withTom(MerVeiledningServiceTest.TOM)
            )
            sykmeldingDokument.bruker = Bruker().withAktoerId("aktorId")*/
        }

        it("varselDato isPresent skal returnere true når siste utbetalingsdag minus 13 uker er første dag i periode") {
/*            Mockito.`when`<Optional<LocalDate>>(sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr")).thenReturn(Optional.of(FOM.plusWeeks(13)))
            Mockito.`when`(planlagtVarselDAO.finnPlanlagteVarsler("aktorId")).thenReturn(emptyList())
            Mockito.`when`(hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.java)).thenReturn(emptyList())*/

            setup()
            val sykmelding: Sykmelding = Sykmelding()
            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual true
        }

        it("varselDato isPresent skal returnere true når siste utbetalingsdag minus 13 uker er siste dag i periode") {
            /*Mockito.`when`<Optional<LocalDate>>(sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr")).thenReturn(Optional.of(MerVeiledningServiceTest.TOM.plusWeeks(13)))
            Mockito.`when`(planlagtVarselDAO.finnPlanlagteVarsler("aktorId")).thenReturn(emptyList())
            Mockito.`when`(hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.java)).thenReturn(emptyList())*/

            setup()
            val sykmelding: Sykmelding = Sykmelding()
            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual true
        }

        it("varselDato isPresent skal returnere false når siste utbetalingsdag minus 13 uker er dagen etter sykmelding") {
            /*Mockito.`when`<Optional<LocalDate>>(sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr")).thenReturn(Optional.of(MerVeiledningServiceTest.TOM.plusDays(1).plusWeeks(13)))
            Mockito.`when`(planlagtVarselDAO.finnPlanlagteVarsler("aktorId")).thenReturn(emptyList())
            Mockito.`when`(hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.java)).thenReturn(emptyList())*/

            setup()
            val sykmelding: Sykmelding = Sykmelding()
            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere false når har planlagt varsel første dag i periode") {
/*            Mockito.`when`<Optional<LocalDate>>(sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr")).thenReturn(Optional.of(MerVeiledningServiceTest.FOM))
            Mockito.`when`(planlagtVarselDAO.finnPlanlagteVarsler("aktorId"))
                    .thenReturn(listOf(
                            PlanlagtVarsel()
                                    .withSendingsdato(MerVeiledningServiceTest.FOM)
                                    .withType(Hendelsestype.MER_VEILEDNING)))
            Mockito.`when`(hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.java)).thenReturn(emptyList())*/

            setup()
            val sykmelding: Sykmelding = Sykmelding()
            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere false når har hendelse første dag i periode") {
/*            Mockito.`when`<Optional<LocalDate>>(sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr")).thenReturn(Optional.of(MerVeiledningServiceTest.FOM))
            Mockito.`when`(planlagtVarselDAO.finnPlanlagteVarsler("aktorId")).thenReturn(emptyList())
            Mockito.`when`(hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.java))
                    .thenReturn(listOf(
                            HendelseMerVeiledning()
                                    .withInntruffetdato(MerVeiledningServiceTest.FOM)
                                    .withType(Hendelsestype.MER_VEILEDNING)))*/

            setup()
            val sykmelding: Sykmelding = Sykmelding()
            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere false når har hendelse 13 uker før periode") {
            /*           Mockito.`when`<Optional<LocalDate>>(sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr")).thenReturn(Optional.of(MerVeiledningServiceTest.FOM))
            Mockito.`when`(planlagtVarselDAO.finnPlanlagteVarsler("aktorId")).thenReturn(emptyList())
            Mockito.`when`(hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.java))
                    .thenReturn(listOf(
                            HendelseMerVeiledning()
                                    .withInntruffetdato(MerVeiledningServiceTest.FOM.minusWeeks(13))
                                    .withType(Hendelsestype.MER_VEILEDNING)))*/

            setup()
            val sykmelding: Sykmelding = Sykmelding()
            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere true når har hendelse 13 uker og en dag før periode") {
/*            Mockito.`when`<Optional<LocalDate>>(sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr")).thenReturn(Optional.of(MerVeiledningServiceTest.FOM))
            Mockito.`when`(planlagtVarselDAO.finnPlanlagteVarsler("aktorId")).thenReturn(emptyList())
            Mockito.`when`(hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.java))
                    .thenReturn(listOf(
                            HendelseMerVeiledning()
                                    .withInntruffetdato(MerVeiledningServiceTest.FOM.minusWeeks(13).minusDays(1))
                                    .withType(Hendelsestype.MER_VEILEDNING)))*/

            setup()
            val sykmelding: Sykmelding = Sykmelding()
            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual true
        }

        it("varselDato isPresent skal returnere false når har hendelse etter periode") {
/*            Mockito.`when`<Optional<LocalDate>>(sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr")).thenReturn(Optional.of(MerVeiledningServiceTest.FOM))
            Mockito.`when`(planlagtVarselDAO.finnPlanlagteVarsler("aktorId")).thenReturn(emptyList())
            Mockito.`when`(hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.java))
                    .thenReturn(listOf(
                            HendelseMerVeiledning()
                                    .withInntruffetdato(MerVeiledningServiceTest.TOM.plusDays(1))
                                    .withType(Hendelsestype.MER_VEILEDNING)))*/

            setup()
            val sykmelding: Sykmelding = Sykmelding()
            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere false når maksdato er mer enn 26 uker før FOM") {
/*            Mockito.`when`<Optional<LocalDate>>(sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr")).thenReturn(Optional.of(MerVeiledningServiceTest.FOM.minusWeeks(26).minusDays(1)))
            Mockito.`when`(planlagtVarselDAO.finnPlanlagteVarsler("aktorId")).thenReturn(emptyList())
            Mockito.`when`(hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.java)).thenReturn(emptyList())*/

            setup()
            val sykmelding: Sykmelding = Sykmelding()
            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere true når maksdato er 26 uker før FOM") {
/*            Mockito.`when`<Optional<LocalDate>>(sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr")).thenReturn(Optional.of(MerVeiledningServiceTest.FOM.minusWeeks(26)))
            Mockito.`when`(planlagtVarselDAO.finnPlanlagteVarsler("aktorId")).thenReturn(emptyList())
            Mockito.`when`(hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.java)).thenReturn(emptyList())*/

            setup()
            val sykmelding: Sykmelding = Sykmelding()
            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual true
        }
    }
})
