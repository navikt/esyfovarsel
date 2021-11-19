package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByType
import no.nav.syfo.varsel.MerVeiledningVarselPlanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ReplanleggingService(val databaseAccess: DatabaseInterface, val merVeiledningVarselPlanner: MerVeiledningVarselPlanner) {

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.ReplanleggingService")

    suspend fun planleggMerVeiledningVarslerPaNytt() {
        log.info("[ReplanleggingService]: Går gjennom alle planlagte 39-ukersvarsel og planlegger dem på nytt")
        val planlagteVarsler = databaseAccess.fetchPlanlagtVarselByType(VarselType.MER_VEILEDNING)
        log.info("[ReplanleggingService]: Replanlegger ${planlagteVarsler.size} varsler av type Mer veiledning")
        planlagteVarsler.forEach{it -> merVeiledningVarselPlanner.processOppfolgingstilfelle(it.aktorId, it.fnr)}
    }
}