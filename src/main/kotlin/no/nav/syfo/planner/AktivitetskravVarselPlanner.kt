package no.nav.syfo.planner

import kotlinx.coroutines.coroutineScope
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deletePlanlagtVarselBySykmeldingerId
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.metrics.tellAktivitetskravPlanlagt
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.syketilfelle.SyketilfellebitService
import no.nav.syfo.utils.VarselUtil

class AktivitetskravVarselPlanner(
    val databaseAccess: DatabaseInterface,
    val syketilfellebitService: SyketilfellebitService,
    val sykmeldingService: SykmeldingService
) {
    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)
    private val AKTIVITETSKRAV_DAGER: Long = 42L

    suspend fun processSyketilfelle(fnr: String, orgnummer: String?) = coroutineScope {
        val oppfolgingstilfellePerson = syketilfellebitService.beregnKOppfolgingstilfelle(fnr) ?: return@coroutineScope

        val validSyketilfelledager = oppfolgingstilfellePerson.tidslinje.filter { varselUtil.isValidSyketilfelledag(it) }.sortedBy { it.dag }

        if (validSyketilfelledager.isNotEmpty() && varselUtil.calculateActualNumberOfDaysInTimeline(validSyketilfelledager) >= AKTIVITETSKRAV_DAGER) {
            val newestSyketilfelledag = validSyketilfelledager.last()
            val oldestSyketilfelledag = validSyketilfelledager.first()

            val fom = oldestSyketilfelledag.prioritertSyketilfellebit!!.fom
            val tom = newestSyketilfelledag.prioritertSyketilfellebit!!.tom

            val aktivitetskravVarselDate = fom.plusDays(AKTIVITETSKRAV_DAGER)

            var ressursIds: MutableSet<String> = HashSet()

            validSyketilfelledager.forEach {
                ressursIds.add(it.prioritertSyketilfellebit!!.ressursId)
            }

            val lagreteVarsler = varselUtil.getPlanlagteVarslerAvType(fnr, VarselType.AKTIVITETSKRAV)

            if (varselUtil.isVarselDatoForIDag(aktivitetskravVarselDate)) {
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds, VarselType.AKTIVITETSKRAV)
            } else if (varselUtil.isVarselDatoEtterTilfelleSlutt(aktivitetskravVarselDate, tom)) {
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds, VarselType.AKTIVITETSKRAV)
            } else if (sykmeldingService.checkSykmeldingStatusForVirksomhet(aktivitetskravVarselDate, fnr, orgnummer).isSykmeldtIJobb) {
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds, VarselType.AKTIVITETSKRAV)
            } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDate }
                    .isNotEmpty()
            ) {
            } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDate }
                    .isEmpty()
            ) {
                if (varselUtil.hasLagreteVarslerForForespurteSykmeldinger(lagreteVarsler, ressursIds)) {
                    databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds, VarselType.AKTIVITETSKRAV)

                    val aktivitetskravVarsel = PlanlagtVarsel(
                        fnr,
                        oppfolgingstilfellePerson.aktorId,
                        orgnummer,
                        ressursIds,
                        VarselType.AKTIVITETSKRAV,
                        aktivitetskravVarselDate
                    )
                    databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                } else {
                    val aktivitetskravVarsel = PlanlagtVarsel(
                        fnr,
                        oppfolgingstilfellePerson.aktorId,
                        orgnummer,
                        ressursIds,
                        VarselType.AKTIVITETSKRAV,
                        aktivitetskravVarselDate
                    )
                    databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                    tellAktivitetskravPlanlagt()
                }
            } else {
                val aktivitetskravVarsel = PlanlagtVarsel(
                    fnr,
                    oppfolgingstilfellePerson.aktorId,
                    orgnummer,
                    ressursIds,
                    VarselType.AKTIVITETSKRAV,
                    aktivitetskravVarselDate
                )
                databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                tellAktivitetskravPlanlagt()
            }
        }
    }
}
