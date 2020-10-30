package no.nav.syfo.service

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.controller.SykepengerRestController
import no.nav.syfo.domain.*
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.*

object MerVeiledningServiceSpek : Spek({

    val sykmelding: Sykmelding = mockk()
    val aktoerIdConsumer: AktoerIdConsumer = mockk()
    val sykepengerRestController: SykepengerRestController = mockk()
    val planlagtVarselService: PlanlagtVarselService = mockk()
    val hendelseDAO: HendelseDAO = mockk()
    val planlagtVarselDAO: PlanlagtVarselDAO = mockk()
    val merVeiledningService = MerVeiledningService(sykepengerRestController, planlagtVarselService, hendelseDAO, planlagtVarselDAO)

    val FOM = LocalDate.of(2020, 1, 1)
    val TOM = LocalDate.of(2020, 1, 20)

    describe("MerVeiledningServiceSpek") {

        fun setup() {
            every { sykmelding.pasientFnr } returns "fnr"
            every { sykmelding.bruker } returns Bruker().withAktoerId("aktorId")
            every { sykmelding.perioder } returns
                    listOf(
                        Periode()
                                .withFom(FOM)
                                .withTom(TOM)
                    )
            every { aktoerIdConsumer.finnAktoerId("fnr") } returns "aktorId"
        }

        it("varselDato isPresent skal returnere true når siste utbetalingsdag minus 13 uker er første dag i periode") {
            setup()

            every { sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr") } returns Optional.of(FOM.plusWeeks(13))
            every { planlagtVarselDAO.finnPlanlagteVarsler("aktorId") } returns emptyList<PlanlagtVarsel>()
            every { hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.simpleName) } returns emptyList<HendelseMerVeiledning>()

            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual true
        }

        it("varselDato isPresent skal returnere true når siste utbetalingsdag minus 13 uker er siste dag i periode") {
            setup()

            every { sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr") } returns Optional.of(TOM.plusWeeks(13))
            every { planlagtVarselDAO.finnPlanlagteVarsler("aktorId") } returns emptyList<PlanlagtVarsel>()
            every { hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.simpleName) } returns emptyList<HendelseMerVeiledning>()

            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual true
        }

        it("varselDato isPresent skal returnere false når siste utbetalingsdag minus 13 uker er dagen etter sykmelding") {
            setup()

            every { sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr") } returns Optional.of(TOM.plusDays(1).plusWeeks(13))
            every { planlagtVarselDAO.finnPlanlagteVarsler("aktorId") } returns emptyList<PlanlagtVarsel>()
            every { hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.simpleName) } returns emptyList<HendelseMerVeiledning>()

            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere false når har planlagt varsel første dag i periode") {
            setup()

            every { sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr") } returns Optional.of(FOM)
            every { planlagtVarselDAO.finnPlanlagteVarsler("aktorId") } returns
                    listOf(
                        PlanlagtVarsel()
                                .withSendingsdato(FOM)
                                .withType(HendelseType.MER_VEILEDNING)
                    )
            every { hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.simpleName) } returns emptyList<HendelseMerVeiledning>()

            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere false når har hendelse første dag i periode") {
            setup()

            every { sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr") } returns Optional.of(FOM)
            every { planlagtVarselDAO.finnPlanlagteVarsler("aktorId") } returns emptyList<PlanlagtVarsel>()
            every { hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.simpleName) } returns
                    listOf(
                            HendelseMerVeiledning()
                                    .withInntruffetdato(FOM)
                                    .withType(HendelseType.MER_VEILEDNING)
                    )

            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere false når har hendelse 13 uker før periode") {
            setup()

            every { sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr") } returns Optional.of(FOM)
            every { planlagtVarselDAO.finnPlanlagteVarsler("aktorId") } returns emptyList<PlanlagtVarsel>()
            every { hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.simpleName) } returns
                    listOf(
                            HendelseMerVeiledning()
                                    .withInntruffetdato(FOM.minusWeeks(13))
                                    .withType(HendelseType.MER_VEILEDNING)
                    )

            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere true når har hendelse 13 uker og en dag før periode") {
            setup()

            every { sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr") } returns Optional.of(FOM)
            every { planlagtVarselDAO.finnPlanlagteVarsler("aktorId") } returns emptyList<PlanlagtVarsel>()
            every { hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.simpleName) } returns
                    listOf(
                            HendelseMerVeiledning()
                                    .withInntruffetdato(FOM.minusWeeks(13).minusDays(1))
                                    .withType(HendelseType.MER_VEILEDNING)
                    )

            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual true
        }

        it("varselDato isPresent skal returnere false når har hendelse etter periode") {
            setup()

            every { sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr") } returns Optional.of(FOM)
            every { planlagtVarselDAO.finnPlanlagteVarsler("aktorId") } returns emptyList<PlanlagtVarsel>()
            every { hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.simpleName) } returns
                    listOf(
                            HendelseMerVeiledning()
                                    .withInntruffetdato(TOM.plusDays(1))
                                    .withType(HendelseType.MER_VEILEDNING)
                    )

            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere false når maksdato er mer enn 26 uker før FOM") {
            setup()

            every { sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr") } returns Optional.of(FOM.minusWeeks(26).minusDays(1))
            every { planlagtVarselDAO.finnPlanlagteVarsler("aktorId") } returns emptyList<PlanlagtVarsel>()
            every { hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.simpleName) } returns emptyList<HendelseMerVeiledning>()

            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual false
        }

        it("varselDato isPresent skal returnere true når maksdato er 26 uker før FOM") {
            setup()

            every { sykepengerRestController.hentSisteSykepengeutbetalingsdato("fnr") } returns Optional.of(FOM.minusWeeks(26))
            every { planlagtVarselDAO.finnPlanlagteVarsler("aktorId") } returns emptyList<PlanlagtVarsel>()
            every { hendelseDAO.finnHendelser("aktorId", HendelseMerVeiledning::class.simpleName) } returns emptyList<HendelseMerVeiledning>()

            merVeiledningService.varselDato(sykmelding).isPresent shouldEqual true
        }
    }
})
