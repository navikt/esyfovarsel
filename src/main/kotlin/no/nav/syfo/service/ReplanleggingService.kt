package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByTypeAndUtsendingsdato
import no.nav.syfo.varsel.AktivitetskravVarselPlanner
import no.nav.syfo.varsel.MerVeiledningVarselPlanner
import no.nav.syfo.varsel.VarselPlanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

class ReplanleggingService(
    val databaseAccess: DatabaseInterface,
    val merVeiledningVarselPlanner: MerVeiledningVarselPlanner,
    val aktivitetskravVarselPlanner: AktivitetskravVarselPlanner
) {

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.ReplanleggingService")

    suspend fun planleggMerVeiledningVarslerPaNytt(fromDate: LocalDate, toDate: LocalDate): Int {
        return planleggVarslerPaNytt(VarselType.MER_VEILEDNING, merVeiledningVarselPlanner, fromDate, toDate)
    }

    suspend fun planleggAktivitetskravVarslerPaNytt(fromDate: LocalDate, toDate: LocalDate): Int {
        return planleggVarslerPaNytt(VarselType.AKTIVITETSKRAV, aktivitetskravVarselPlanner, fromDate, toDate)
    }

    suspend fun planleggVarslerPaNytt(varselType: VarselType, planlegger: VarselPlanner, fromDate: LocalDate, toDate: LocalDate): Int {
        log.info("[ReplanleggingService]: Går gjennom alle planlagte $varselType-varsler mellom $fromDate og $toDate og planlegger dem på nytt")
        val planlagteVarsler = databaseAccess.fetchPlanlagtVarselByTypeAndUtsendingsdato(varselType, fromDate, toDate)
        val size = planlagteVarsler.size
        log.info("[ReplanleggingService]: Replanlegger $size varsler av type $varselType")
        planlagteVarsler.forEach { planlegger.processOppfolgingstilfelle(it.aktorId, it.fnr) }
        return size
    }
}