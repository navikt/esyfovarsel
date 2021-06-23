package no.nav.syfo.util

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

class VarselUtil(private val databaseAccess: DatabaseInterface) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.VarselUtil")

    fun isVarselDatoForIDag(varselDato: LocalDate): Boolean {
        return varselDato.isBefore(LocalDate.now())
    }

    fun isVarselDatoEtterTilfelleSlutt(varselDato: LocalDate, tilfelleSluttDato: LocalDate): Boolean {
        return tilfelleSluttDato.isEqual(varselDato) || varselDato.isAfter(tilfelleSluttDato)
    }

    fun isVarselPlanlagt(fnr: String, varselType: VarselType, varselDato: LocalDate): Boolean {
        val varsler = databaseAccess.fetchPlanlagtVarselByFnr(fnr)

        log.info("[AKTIVITETSKRAV_VARSEL]: tidligere lagrete varsler:  $varsler")//TODO: delete

        return varsler.filter { varselType.name == it.type }
            .filter { it.utsendingsdato === varselDato }
            .any()
    }

    fun isVarselSendUt(fnr: String, varselType: VarselType, varselDato: LocalDate): Boolean {
        return databaseAccess.fetchPlanlagtVarselByFnr(fnr)
            .filter { varselType.name == it.type }
            .filter { it.utsendingsdato === varselDato }
            .filter { it.utsendingsdato.isBefore(LocalDate.now()) || it.utsendingsdato == LocalDate.now() }
            .any()
    }
}
