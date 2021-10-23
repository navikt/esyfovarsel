package no.nav.syfo.utils

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.fetchSykmeldingerIdByPlanlagtVarselsUUID
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.Oppfolgingstilfelle39Uker
import java.time.LocalDate

val antallUker39UkersVarsel = 39L
val antallDager39UkersVarsel = antallUker39UkersVarsel * 7L

class VarselUtil(private val databaseAccess: DatabaseInterface) {
    fun isVarselDatoForIDag(varselDato: LocalDate): Boolean {
        return varselDato.isBefore(LocalDate.now())
    }

    fun varselDate39Uker(tilfelle: Oppfolgingstilfelle39Uker): LocalDate? {
        val dagerSykmeldt = tilfelle.antallSykefravaersDagerTotalt
        val tom =  tilfelle.tom

        val varselDatoTomOffset = (dagerSykmeldt - antallDager39UkersVarsel)
        val varselDato = tom.minusDays(varselDatoTomOffset)

        val today = LocalDate.now()

        return if (tom.isEqualOrAfter(varselDato) && today.isEqualOrBefore(varselDato))
            varselDato
        else
            null
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
}
