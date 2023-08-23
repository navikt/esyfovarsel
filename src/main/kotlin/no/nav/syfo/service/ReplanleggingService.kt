package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByTypeAndUtsendingsdato
import no.nav.syfo.planner.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

class ReplanleggingService(
    val databaseAccess: DatabaseInterface,
    val aktivitetskravVarselPlanner: AktivitetskravVarselPlanner
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.ReplanleggingService")

    suspend fun planleggAktivitetskravVarslerPaNytt(fromDate: LocalDate, toDate: LocalDate): Int {
        log.info("[ReplanleggingService]: Går gjennom alle planlagte ${VarselType.AKTIVITETSKRAV}-varsler mellom $fromDate og $toDate og planlegger dem på nytt")
        val planlagteVarsler = databaseAccess.fetchPlanlagtVarselByTypeAndUtsendingsdato(VarselType.AKTIVITETSKRAV, fromDate, toDate)
        val size = planlagteVarsler.size
        log.info("[ReplanleggingService]: Replanlegger $size varsler av type ${VarselType.AKTIVITETSKRAV}")
        planlagteVarsler.forEach { aktivitetskravVarselPlanner.processSyketilfelle(it.fnr, it.orgnummer) }
        log.info("[ReplanleggingService]: Planla $size varsler av type ${VarselType.AKTIVITETSKRAV}")
        return size
    }
}
