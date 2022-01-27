package no.nav.syfo.utils

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.fetchSykmeldingerIdByPlanlagtVarselsUUID
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.Oppfolgingstilfelle39Uker
import java.time.LocalDate

val antallUker39UkersVarsel = 39L
val antallDager39UkersVarsel = antallUker39UkersVarsel * 7L + 1

class VarselUtil(private val databaseAccess: DatabaseInterface) {
    fun isVarselDateBeforeToday(varselDato: LocalDate): Boolean {
        return varselDato.isBefore(LocalDate.now())
    }

    fun varselDate39Uker(tilfelle: Oppfolgingstilfelle39Uker): LocalDate? {
        val varselDatoTomOffset = (tilfelle.antallSykefravaersDagerTotalt - antallDager39UkersVarsel)
        val varselDato = tilfelle.tom.minusDays(varselDatoTomOffset)

        val isAntallSykedagerPast39Uker = tilfelle.antallSykefravaersDagerTotalt >= antallDager39UkersVarsel
        val isTomAfterVarselDato = tilfelle.tom.isEqualOrAfter(varselDato)
        val isVarselDatoExpired = varselDato.isBefore(LocalDate.now())

        return if (isAntallSykedagerPast39Uker && isTomAfterVarselDato && !isVarselDatoExpired) varselDato else null
    }

    fun isVarselDateAfterTilfelleEnd(varselDate: LocalDate, tilfelleEndDate: LocalDate): Boolean {
        return tilfelleEndDate.isEqual(varselDate) || varselDate.isAfter(tilfelleEndDate)
    }

    fun getPlanlagteVarslerOfType(fnr: String, varselType: VarselType): List<PPlanlagtVarsel> {
        return databaseAccess.fetchPlanlagtVarselByFnr(fnr)
            .filter { it.type == varselType.name }
    }

    fun hasSavedVarslerWithRequestedRessursIds(planlagteVarsler: List<PPlanlagtVarsel>, ressursIds: Set<String>): Boolean {
        val savedRessursIds = mutableSetOf<Set<String>>()
        for (v: PPlanlagtVarsel in planlagteVarsler) {
            val sm = databaseAccess.fetchSykmeldingerIdByPlanlagtVarselsUUID(v.uuid)
            savedRessursIds.add(sm.toSet())
        }
        val savedRessursIdsSet = savedRessursIds.flatten().toSet()
        return (ressursIds intersect savedRessursIdsSet).isNotEmpty()
    }
}
