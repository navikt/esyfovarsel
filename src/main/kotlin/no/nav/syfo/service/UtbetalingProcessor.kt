package no.nav.syfo.service

import no.nav.syfo.db.*
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import java.time.LocalDate

class UtbetalingProcessor(private val databaseInterface: DatabaseInterface) {
    fun processUtbetalingSpleisEvent(utbetaling: UtbetalingUtbetalt) {
        databaseInterface.storeSpleisUtbetaling(utbetaling)
    }

    fun processInfotrygdEvent(fnr: String, sykepengerMaxDate: LocalDate, utbetaltTilDate: LocalDate, gjenstaendeSykepengedager: Int) {
        databaseInterface.storeInfotrygdUtbetaling(fnr, sykepengerMaxDate, utbetaltTilDate, gjenstaendeSykepengedager)
    }
}
