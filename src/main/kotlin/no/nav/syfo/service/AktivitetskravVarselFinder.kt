package no.nav.syfo.service

import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByUtsendingsdatoAndType
import java.time.LocalDate

class AktivitetskravVarselFinder(
    private val databaseAccess: DatabaseInterface,
    private val pdlConsumer: PdlConsumer,
) {
    fun findAktivitetskravVarslerToSendToday(): List<PPlanlagtVarsel> {
        return databaseAccess.fetchPlanlagtVarselByUtsendingsdatoAndType(
            LocalDate.now(),
            VarselType.AKTIVITETSKRAV.name,
        ).filter { isBrukerYngreEnn70Ar(it.fnr) }
    }

    fun isBrukerYngreEnn70Ar(fnr: String): Boolean {
        return pdlConsumer.isBrukerYngreEnnGittMaxAlder(fnr, 70)
    }
}
