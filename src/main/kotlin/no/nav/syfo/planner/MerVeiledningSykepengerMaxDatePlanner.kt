package no.nav.syfo.planner

import no.nav.syfo.db.*
import no.nav.syfo.utils.isEqualOrBefore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

class MerVeiledningSykepengerMaxDatePlanner(private val databaseInterface: DatabaseInterface) : VarselPlannerMaxDate {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.planner.MerVeiledningSykepengerMaxDatePlanner")
    override val name: String = "MER_VEILEDNING_VARSEL_MAX_DATE"

    override fun processNewMaxDate(fnr: String, sykepengerMaxDate: LocalDate, source: SykepengerMaxDateSource) {
        val currentStoredMaxDateForSykmeldt = databaseInterface.fetchMaxDateByFnr(fnr);

        if (LocalDate.now().isEqualOrBefore(sykepengerMaxDate)) {
            if (currentStoredMaxDateForSykmeldt == null) {
                //Store new data if none exists from before
                databaseInterface.storeSykepengerMaxDate(sykepengerMaxDate, fnr, source.name)
            } else {
                //Update data if change exists
                databaseInterface.updateMaxDateByFnr(sykepengerMaxDate, fnr, source.name)
            }
        } else {
            if (currentStoredMaxDateForSykmeldt != null) {
                //Delete data if maxDate is older than today
                databaseInterface.deleteMaxDateByFnr(fnr)
            }
        }
    }
}

enum class SykepengerMaxDateSource {
    INFOTRYGD,
    SPLEIS
}
