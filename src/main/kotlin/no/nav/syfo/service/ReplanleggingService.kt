package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByType
import no.nav.syfo.varsel.AktivitetskravVarselPlanner
import no.nav.syfo.varsel.MerVeiledningVarselPlanner
import no.nav.syfo.varsel.VarselPlanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ReplanleggingService(val databaseAccess: DatabaseInterface, val merVeiledningVarselPlanner: MerVeiledningVarselPlanner, val aktivitetskravVarselPlanner: AktivitetskravVarselPlanner) {

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.ReplanleggingService")

    suspend fun planleggMerVeiledningVarslerPaNytt() {
        planleggVarslerPaNytt(VarselType.MER_VEILEDNING, merVeiledningVarselPlanner)
    }

    suspend fun planleggAktivitetskravVarslerPaNytt() {
        planleggVarslerPaNytt(VarselType.AKTIVITETSKRAV, aktivitetskravVarselPlanner)
    }

    suspend fun planleggVarslerPaNytt(varselType: VarselType, planlegger: VarselPlanner) {
        log.info("[ReplanleggingService]: Går gjennom alle planlagte $varselType-varsler og planlegger dem på nytt")
        val planlagteVarsler = databaseAccess.fetchPlanlagtVarselByType(varselType)
        log.info("[ReplanleggingService]: Replanlegger ${planlagteVarsler.size} varsler av type $varselType")
        planlagteVarsler.forEach{it -> planlegger.processOppfolgingstilfelle(it.aktorId, it.fnr)}
    }
}