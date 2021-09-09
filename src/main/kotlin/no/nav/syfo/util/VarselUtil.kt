package no.nav.syfo.util

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.fetchSykmeldingerIdByPlanlagtVarselsUUID
import no.nav.syfo.db.fetchUtsendtVarselByFnr
import no.nav.syfo.utils.dateIsInInterval
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

    fun kanNyttVarselSendes(fnr: String, varselType: VarselType): Boolean {
        val utsendtVarsel = databaseAccess.fetchUtsendtVarselByFnr(fnr)
            .filter { it.type == varselType.name }

        if (utsendtVarsel.isEmpty()) {
            return true
        }

        utsendtVarsel.sortedBy { it.utsendtTidspunkt }
        val sisteUtsendeVarsel = utsendtVarsel.last()
        val sisteGangVarselBleUtsendt = sisteUtsendeVarsel.utsendtTidspunkt.toLocalDate()
        val sisteDagISykefravaeret = sisteGangVarselBleUtsendt.plusWeeks(13L)
        val dagsDato = LocalDate.now()
        return !dateIsInInterval(dagsDato, sisteGangVarselBleUtsendt, sisteDagISykefravaeret)
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
}
