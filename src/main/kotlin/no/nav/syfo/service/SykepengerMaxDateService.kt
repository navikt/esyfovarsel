package no.nav.syfo.service

import java.time.*
import no.nav.syfo.consumer.pdl.*
import no.nav.syfo.db.*
import no.nav.syfo.db.domain.PMaksDato
import no.nav.syfo.kafka.consumers.infotrygd.domain.*
import no.nav.syfo.kafka.consumers.utbetaling.domain.*

class SykepengerMaxDateService(private val databaseInterface: DatabaseInterface, private val pdlConsumer: PdlConsumer) {

    suspend fun processUtbetalingSpleisEvent(utbetaling: UtbetalingSpleis) {
        val fnr = utbetaling.fødselsnummer
        processFodselsdato(fnr)
        databaseInterface.storeSpleisUtbetaling(utbetaling)
    }

    fun getSykepengerMaxDate(fnr: String): PMaksDato? {
        return databaseInterface.fetchMaksDatoByFnr(fnr)
    }

    suspend fun processInfotrygdEvent(fnr: String, sykepengerMaxDate: LocalDate, utbetaltTilDate: LocalDate, gjenstaendeSykepengedager: Int, source: InfotrygdSource) {
        processFodselsdato(fnr)
        databaseInterface.storeInfotrygdUtbetaling(fnr, sykepengerMaxDate, utbetaltTilDate, gjenstaendeSykepengedager, source)
    }

    private suspend fun processFodselsdato(fnr: String) {
        val lagretFodselsdato = databaseInterface.fetchFodselsdatoByFnr(fnr)
        if (lagretFodselsdato.isEmpty()){
            val pdlFodsel = pdlConsumer.hentPerson(fnr)?.hentPerson?.foedsel
            val fodselsdato = if (!pdlFodsel.isNullOrEmpty()) pdlFodsel.first().foedselsdato else null
            databaseInterface.storeFodselsdato(fnr, fodselsdato)
        }
    }
}
