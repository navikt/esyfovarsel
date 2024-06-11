package no.nav.syfo.service

import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PMaksDato
import no.nav.syfo.db.fetchFodselsdatoByFnr
import no.nav.syfo.db.fetchMaksDatoByFnr
import no.nav.syfo.db.storeFodselsdato
import no.nav.syfo.db.storeInfotrygdUtbetaling
import no.nav.syfo.db.storeSpleisUtbetaling
import no.nav.syfo.kafka.consumers.infotrygd.domain.InfotrygdSource
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingSpleis
import java.time.LocalDate

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
            val fodselsdato = pdlConsumer.hentPerson(fnr)?.hentPerson?.foedselsdato?.foedselsdato
            databaseInterface.storeFodselsdato(fnr, fodselsdato)
        }
    }
}
