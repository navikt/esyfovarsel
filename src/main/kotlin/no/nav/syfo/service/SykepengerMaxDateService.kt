package no.nav.syfo.service

import java.time.LocalDate
import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deleteSykepengerMaxDateByFnr
import no.nav.syfo.db.fetchFodselsdatoByFnr
import no.nav.syfo.db.fetchForelopigBeregnetSluttPaSykepengerByFnr
import no.nav.syfo.db.fetchSykepengerMaxDateByFnr
import no.nav.syfo.db.storeFodselsdato
import no.nav.syfo.db.storeInfotrygdUtbetaling
import no.nav.syfo.db.storeSpleisUtbetaling
import no.nav.syfo.db.storeSykepengerMaxDate
import no.nav.syfo.db.updateSykepengerMaxDateByFnr
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import no.nav.syfo.utils.isEqualOrBefore

class SykepengerMaxDateService(private val databaseInterface: DatabaseInterface, private val pdlConsumer: PdlConsumer) {
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
        val fnr = utbetaling.fødselsnummer
        processNewMaxDate(fnr, utbetaling.foreløpigBeregnetSluttPåSykepenger, SykepengerMaxDateSource.SPLEIS)
        processFodselsdato(fnr)
        databaseInterface.storeSpleisUtbetaling(utbetaling)
    }

    fun getSykepengerMaxDate(fnr: String): LocalDate? {
        return databaseInterface.fetchForelopigBeregnetSluttPaSykepengerByFnr(fnr)
    }

    fun processInfotrygdEvent(fnr: String, sykepengerMaxDate: LocalDate, utbetaltTilDate: LocalDate, gjenstaendeSykepengedager: Int) {
        processNewMaxDate(fnr, sykepengerMaxDate, SykepengerMaxDateSource.INFOTRYGD)
        processFodselsdato(fnr)
        databaseInterface.storeInfotrygdUtbetaling(fnr, sykepengerMaxDate, utbetaltTilDate, gjenstaendeSykepengedager)
    }

    private fun processFodselsdato(fnr: String) {
        val lagretFodselsdato = databaseInterface.fetchFodselsdatoByFnr(fnr)
        if (lagretFodselsdato.isNullOrEmpty()){
            val pdlFodsel = pdlConsumer.hentPerson(fnr)?.hentPerson?.foedsel
            val fodselsdato = if (!pdlFodsel.isNullOrEmpty()) pdlFodsel.first().foedselsdato else null
            databaseInterface.storeFodselsdato(fnr, fodselsdato)
        }
    }

}

enum class SykepengerMaxDateSource {
    INFOTRYGD,
    SPLEIS
}
