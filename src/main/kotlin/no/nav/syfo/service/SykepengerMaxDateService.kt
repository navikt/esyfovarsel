package no.nav.syfo.service

import no.nav.syfo.db.*
import no.nav.syfo.utils.isEqualOrBefore
import java.time.LocalDate

class SykepengerMaxDateService(private val databaseInterface: DatabaseInterface) {
    fun processNewMaxDate(fnr: String, sykepengerMaxDate: LocalDate?, source: SykepengerMaxDateSource) {
        val currentStoredMaxDateForSykmeldt = databaseInterface.fetchSykepengerMaxDateByFnr(fnr)

        when {
            sykepengerMaxDate == null -> {
                databaseInterface.deleteSykepengerMaxDateByFnr(fnr)
            }
            LocalDate.now().isEqualOrBefore(sykepengerMaxDate) -> {
                if (currentStoredMaxDateForSykmeldt == null) {
                    //Store new data if none exists from before
                    databaseInterface.storeSykepengerMaxDate(sykepengerMaxDate, fnr, source.name)
                } else {
                    //Update data if change exists
                    databaseInterface.updateSykepengerMaxDateByFnr(sykepengerMaxDate, fnr, source.name)
                }
            }
            else -> {
                if (currentStoredMaxDateForSykmeldt != null) {
                    //Delete data if maxDate is older than today
                    databaseInterface.deleteSykepengerMaxDateByFnr(fnr)
                }
            }
        }
    }

    fun getSykepengerMaxDate(fnr: String): LocalDate? {
        return databaseInterface.fetchSykepengerMaxDateByFnr(fnr)
    }
}

enum class SykepengerMaxDateSource {
    INFOTRYGD,
    SPLEIS
}
