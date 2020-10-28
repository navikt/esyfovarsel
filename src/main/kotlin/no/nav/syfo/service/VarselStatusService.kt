package no.nav.syfo.service

import no.nav.syfo.domain.Hendelse
import no.nav.syfo.domain.HendelseType
import no.nav.syfo.domain.PlanlagtVarsel
import no.nav.syfo.domain.Sykmelding
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselStatusService(
        hendelseService: HendelseService,
        planlagtVarselService: PlanlagtVarselService
) {
    val LOGGER: Logger = LoggerFactory.getLogger(this::class.simpleName)

    var planlagtVarselService: PlanlagtVarselService = planlagtVarselService
    var hendelseService: HendelseService = hendelseService

    fun erAlleredePlanlagt(aktoerId: String, sykmeldinger: List<Sykmelding>, hendelseType: HendelseType): Boolean {
        val alleredePlanlagt: Boolean = planlagtVarselService.finnPlanlagteVarsler(aktoerId)
                .filter { planlagtVarsel: PlanlagtVarsel -> planlagtVarsel.type.equals(hendelseType) }
                .any { planlagtVarsel ->
                    sykmeldinger
                            .map { sykmelding -> sykmelding.meldingId }
                            .any { sykmeldingId -> sykmeldingId.equals(planlagtVarsel.ressursId) }
                }

        if (alleredePlanlagt) {
            LOGGER.info("Planlegger ikke aktivitetskravvarsel: Varsel er allerede planlagt for sykeforløpet!")
        }

        return alleredePlanlagt
    }

    fun harAlleredeBlittSendt(aktoerId: String, sykmeldinger: List<Sykmelding>, hendelseType: HendelseType): Boolean {
        val alleredeSendt: Boolean = hendelseService.finnHendelseTypeVarsler(aktoerId)
                .filter { hendelse: Hendelse -> hendelseType.equals(hendelse.type) }
                .any { hendelse ->
                    sykmeldinger
                            .map { sykmelding: Sykmelding -> sykmelding.id }
                            .any { sykmeldingId: Long -> sykmeldingId == hendelse.sykmelding?.id }

                }

        if (alleredeSendt) {
            LOGGER.info("Planlegger ikke aktivitetskravvarsel: Varsel er allerede sendt for sykeforløpet!")
        }

        return alleredeSendt
    }
}
