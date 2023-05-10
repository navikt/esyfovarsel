package no.nav.syfo.service

import java.time.*
import no.nav.syfo.consumer.pdl.*
import no.nav.syfo.db.*
import no.nav.syfo.db.domain.PMaksDato
import no.nav.syfo.kafka.consumers.infotrygd.domain.*
import no.nav.syfo.kafka.consumers.utbetaling.domain.*
import no.nav.syfo.utils.*

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

    fun getSykepengerMaxDate(fnr: String): PMaksDato? {
        return databaseInterface.fetchMaksDatoByFnr(fnr)
    }

    fun processInfotrygdEvent(fnr: String, sykepengerMaxDate: LocalDate, utbetaltTilDate: LocalDate, gjenstaendeSykepengedager: Int, source: InfotrygdSource) {
        processNewMaxDate(fnr, sykepengerMaxDate, SykepengerMaxDateSource.INFOTRYGD)
        processFodselsdato(fnr)
        databaseInterface.storeInfotrygdUtbetaling(fnr, sykepengerMaxDate, utbetaltTilDate, gjenstaendeSykepengedager, source)
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
