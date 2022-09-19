package no.nav.syfo.service

import no.nav.syfo.db.*
import java.time.LocalDate

class SykepengerMaxDateService(private val databaseInterface: DatabaseInterface) {

    fun saveOrUpdateSykepengerMaxDate(fnr: String, sykepengerMaxDate: LocalDate, source: SykepengerMaxDateSource) {
        val currentStoredMaxDateForSykmeldt = databaseInterface.fetchMaxDateByFnr(fnr);

        if (currentStoredMaxDateForSykmeldt != null && LocalDate.now().isAfter(sykepengerMaxDate)) {
            //Delete data if maxDate is older than today
            databaseInterface.deleteMaxDateByFnr(fnr)
        } else if (currentStoredMaxDateForSykmeldt == null) {
            //Store new data if none exists from before
            databaseInterface.storeSykepengerMaxDate(sykepengerMaxDate, fnr, source.name)
        } else {
            //Update data if change exists
            databaseInterface.updateMaxDateByFnr(sykepengerMaxDate, fnr, source.name)
        }
    }
}

enum class SykepengerMaxDateSource {
    INFOTRYGD,
    SPLEIS
}