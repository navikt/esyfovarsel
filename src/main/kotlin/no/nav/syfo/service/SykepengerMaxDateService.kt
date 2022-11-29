package no.nav.syfo.service

import no.nav.syfo.db.*
import no.nav.syfo.kafka.consumers.infotrygd.domain.InfotrygdSource
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
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

    fun processUtbetalingSpleisEvent(utbetaling: UtbetalingUtbetalt) {
        processNewMaxDate(utbetaling.fødselsnummer, utbetaling.foreløpigBeregnetSluttPåSykepenger, SykepengerMaxDateSource.SPLEIS)
        databaseInterface.storeSpleisUtbetaling(utbetaling)
    }

    fun getSykepengerMaxDate(fnr: String): LocalDate? {
        return databaseInterface.fetchForelopigBeregnetSluttPaSykepengerByFnr(fnr)
    }

    fun processInfotrygdEvent(fnr: String, sykepengerMaxDate: LocalDate, utbetaltTilDate: LocalDate, gjenstaendeSykepengedager: Int, source: InfotrygdSource) {
        processNewMaxDate(fnr, sykepengerMaxDate, SykepengerMaxDateSource.INFOTRYGD)
        databaseInterface.storeInfotrygdUtbetaling(fnr, sykepengerMaxDate, utbetaltTilDate, gjenstaendeSykepengedager, source)
    }

}

enum class SykepengerMaxDateSource {
    INFOTRYGD,
    SPLEIS
}
