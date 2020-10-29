package no.nav.syfo.service

import no.nav.syfo.annotation.Mockable
import no.nav.syfo.domain.PlanlagtVarsel
import no.nav.syfo.domain.Sykmelding
import java.time.LocalDate

@Mockable
class PlanlagtVarselService {

    fun finnPlanlagteVarsler(aktoerId: String): List<PlanlagtVarsel> {
        return emptyList()
    }

    fun planleggVarselMerVeiledning(sykmelding: Sykmelding, dato: LocalDate) {

    }
}
