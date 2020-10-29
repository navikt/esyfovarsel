package no.nav.syfo.service

import no.nav.syfo.controller.SykepengerRestController
import no.nav.syfo.domain.*
import no.nav.syfo.util.compare
import no.nav.syfo.util.hentSenesteTOM
import no.nav.syfo.util.hentTidligsteFOM
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*
import java.util.function.Predicate

class MerVeiledningService(
        sykepengerRestController: SykepengerRestController,
        planlagtVarselService: PlanlagtVarselService,
        hendelseDAO: HendelseDAO,
        planlagtVarselDAO: PlanlagtVarselDAO
) {
    val LOGGER: Logger = LoggerFactory.getLogger(this::class.simpleName)

    val sykepengerRestController = sykepengerRestController
    val planlagtVarselService = planlagtVarselService
    val hendelseDAO = hendelseDAO
    val planlagtVarselDAO = planlagtVarselDAO

    fun planleggVarsel(sykmelding: Sykmelding, dato: LocalDate) {
        LOGGER.info("MER_VEILEDNING: Planlegger varsel for sykmelding {}, på dato {}", sykmelding.meldingId, dato)
        planlagtVarselService.planleggVarselMerVeiledning(sykmelding, dato)
    }

    fun varselDato(sykmelding: Sykmelding): Optional<LocalDate> {
        return sykepengerRestController.hentSisteSykepengeutbetalingsdato(sykmelding.pasientFnr)
                .filter { maksDato: LocalDate -> compare(maksDato).isEqualOrAfter(hentTidligsteFOM(sykmelding).minusWeeks(26)) }
                .map { maksDato: LocalDate -> maksDato.minusWeeks(13) }
                .filter { trettenUkerForSisteUtbetalingsdato: LocalDate -> compare(trettenUkerForSisteUtbetalingsdato).isBeforeOrEqual(hentSenesteTOM(sykmelding)) }
                .filter { ignore: LocalDate -> !hendelseEllerVarselAlleredeOpprettet().test(sykmelding) }
    }

    private fun hendelseEllerVarselAlleredeOpprettet(): Predicate<Sykmelding> {
        val harEksisterendeHendelse: Predicate<Sykmelding> = Predicate { sd: Sykmelding ->
            hendelseDAO.finnHendelser(sd.bruker.aktoerId, HendelseMerVeiledning::class.simpleName).stream()
                    .filter { hendelse: HendelseMerVeiledning -> hendelse.type == HendelseType.MER_VEILEDNING }
                    .anyMatch { hendelse: HendelseMerVeiledning -> compare(hendelse.intruffetdato).isEqualOrAfter(hentTidligsteFOM(sd).minusWeeks(13)) }
        }
        val finnesPlanlagtVarsel: Predicate<Sykmelding> = Predicate { sd: Sykmelding ->
            planlagtVarselDAO.finnPlanlagteVarsler(sd.bruker.aktoerId).stream()
                    .anyMatch { planlagtVarsel: PlanlagtVarsel -> HendelseType.MER_VEILEDNING == planlagtVarsel.type }
        }

        return harEksisterendeHendelse.or(finnesPlanlagtVarsel)
    }
}
