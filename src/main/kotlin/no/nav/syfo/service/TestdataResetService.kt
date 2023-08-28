package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deleteMikrofrontendSynlighetByFnr
import no.nav.syfo.db.deletePlanlagtVarselByVarselId
import no.nav.syfo.db.deleteSyketilfellebitByFnr
import no.nav.syfo.db.deleteUtbetalingInfotrygdByFnr
import no.nav.syfo.db.deleteUtbetalingSpleisByFnr
import no.nav.syfo.db.deleteUtsendtVarselByFnr
import no.nav.syfo.db.deleteUtsendtVarselFeiletByFnr
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.domain.PersonIdent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TestdataResetService(
    private val database: DatabaseInterface,
) {

    private val log: Logger = LoggerFactory.getLogger(TestdataResetService::class.qualifiedName)
    fun resetTestdata(fnr: PersonIdent) {
        log.info(
            "Nullstiller testdata for arbeidstaker ${fnr.value}. Fjerner mikrofrontend på Min side. " +
                    "Sletter planlagte og utsendte varsler, feilede varsler, syketilfellebiter og utbetalinger."
        )

        // Slette utsendte varsler
        database.deleteUtsendtVarselByFnr(fnr)

        // Slette planlagte varsler
        val planlagteVarsler = database.fetchPlanlagtVarselByFnr(fnr.value)
        planlagteVarsler.forEach { database.deletePlanlagtVarselByVarselId(it.uuid) }

        // Slette syketilfellebiter
        database.deleteSyketilfellebitByFnr(fnr)

        // Slette/skjule mikrofrontend
        database.deleteMikrofrontendSynlighetByFnr(fnr)

        // Slette utsendt varsel feilet
        database.deleteUtsendtVarselFeiletByFnr(fnr)

        // Slette utbetalinger fra Spleis
        database.deleteUtbetalingSpleisByFnr(fnr)

        // Slette utbetalinger fra infotrygd
        database.deleteUtbetalingInfotrygdByFnr(fnr)
    }
}
