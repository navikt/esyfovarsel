package no.nav.syfo.util

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.fetchSykmeldingerIdByPlanlagtVarselsUUID
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

    fun getPlanlagteVarslerAvType(fnr: String, varselType: VarselType): List<PPlanlagtVarsel> {
        return databaseAccess.fetchPlanlagtVarselByFnr(fnr)
            .filter { it.type == varselType.name }
    }

    fun hasLagreteVarslerForForespurteSykmeldinger(planlagteVarsler: List<PPlanlagtVarsel>, ressursIds: Set<String>): Boolean {
        val gjeldendeSykmeldinger = mutableSetOf<Set<String>>()
        for (v: PPlanlagtVarsel in planlagteVarsler) {
            val sm = databaseAccess.fetchSykmeldingerIdByPlanlagtVarselsUUID(v.uuid)
            gjeldendeSykmeldinger.add(sm.toSet())
        }
        val gjeldendeSykmeldingerSet = gjeldendeSykmeldinger.flatten().toSet()
        return (ressursIds intersect gjeldendeSykmeldingerSet).isNotEmpty()
    }

    fun isVarselSendUt(fnr: String, varselType: VarselType, varselDato: LocalDate): Boolean {
        return databaseAccess.fetchPlanlagtVarselByFnr(fnr)
            .filter { varselType.name == it.type }
            .filter { it.utsendingsdato == varselDato }
            .filter { it.utsendingsdato.isBefore(LocalDate.now()) || it.utsendingsdato == LocalDate.now() }
            .any()
    }
}
