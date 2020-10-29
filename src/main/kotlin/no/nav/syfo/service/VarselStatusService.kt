package no.nav.syfo.service

import no.nav.syfo.domain.Hendelse
import no.nav.syfo.domain.HendelseType
import no.nav.syfo.domain.PlanlagtVarsel
import no.nav.syfo.domain.Sykmelding
import no.nav.syfo.logger

class VarselStatusService(
        hendelseService: HendelseService,
        planlagtVarselService: PlanlagtVarselService
) {
    val LOGGER = logger()
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
            LOGGER.info("Planlegger ikke {}: Varsel er allerede planlagt for sykeforløpet!", hendelseType)
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
            LOGGER.info("Planlegger ikke {}: Varsel er allerede sendt for sykeforløpet!", hendelseType)
        }

        return alleredeSendt
    }
}
