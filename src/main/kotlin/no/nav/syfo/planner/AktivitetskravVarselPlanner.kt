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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AktivitetskravVarselPlanner(
    val databaseAccess: DatabaseInterface,
    val syketilfellebitService: SyketilfellebitService,
    val sykmeldingService: SykmeldingService
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.AktivitetskravVarselPlannerSyketilfellebit")
    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)
    private val AKTIVITETSKRAV_DAGER: Long = 42L
    private val name: String = "AKTIVITETSKRAV_VARSEL"

    suspend fun processSyketilfelle(fnr: String, orgnummer: String?) = coroutineScope {
        val oppfolgingstilfellePerson = syketilfellebitService.beregnKOppfolgingstilfelle(fnr) ?: return@coroutineScope

        val validSyketilfelledager = oppfolgingstilfellePerson.tidslinje.filter { varselUtil.isValidSyketilfelledag(it) }.sortedBy { it.dag }

        if (validSyketilfelledager.isNotEmpty() && varselUtil.calculateActualNumberOfDaysInTimeline(validSyketilfelledager) >= AKTIVITETSKRAV_DAGER) {
            val newestSyketilfelledag = validSyketilfelledager.last()
            val oldestSyketilfelledag = validSyketilfelledager.first()

            val fom = oldestSyketilfelledag.prioritertSyketilfellebit!!.fom
            val tom = newestSyketilfelledag.prioritertSyketilfellebit!!.tom

            val aktivitetskravVarselDate = fom.plusDays(AKTIVITETSKRAV_DAGER)

            log.info("-$name-: oppfolgingstilfellePerson.fom er -$fom-")
            log.info("-$name-: oppfolgingstilfellePerson.tom er -$tom-")

            var ressursIds: MutableSet<String> = HashSet()

            validSyketilfelledager.forEach {
                ressursIds.add(it.prioritertSyketilfellebit!!.ressursId)
            }

            log.info("-$name-: relevante -FOM, TOM, DATO, RESSURS_IDS: $fom, $tom, $aktivitetskravVarselDate, $ressursIds-")

            val lagreteVarsler = varselUtil.getPlanlagteVarslerAvType(fnr, VarselType.AKTIVITETSKRAV)

            if (varselUtil.isVarselDatoForIDag(aktivitetskravVarselDate)) {
                log.info("-$name-: Beregnet dato for varsel er før i dag, sletter tidligere planlagt varsel om det finnes i DB. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds, VarselType.AKTIVITETSKRAV)
            } else if (varselUtil.isVarselDatoEtterTilfelleSlutt(aktivitetskravVarselDate, tom)) {
                log.info("-$name-: Tilfelle er kortere enn 6 uker, sletter tidligere planlagt varsel om det finnes i DB. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds, VarselType.AKTIVITETSKRAV)
            } else if (sykmeldingService.checkSykmeldingStatusForVirksomhet(aktivitetskravVarselDate, fnr, orgnummer).isSykmeldtIJobb) {
                log.info("-$name-: Sykmeldingsgrad er < enn 100% på beregnet varslingsdato, sletter tidligere planlagt varsel om det finnes i DB. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds, VarselType.AKTIVITETSKRAV)
            } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDate }
                    .isNotEmpty()
            ) {
                log.info("-$name-: varsel med samme utsendingsdato er allerede planlagt. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
            } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDate }
                    .isEmpty()
            ) {
                log.info("-$name-: sjekker om det finnes varsler med samme id. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
                if (varselUtil.hasLagreteVarslerForForespurteSykmeldinger(lagreteVarsler, ressursIds)) {
                    log.info("-$name-: sletter tidligere varsler for. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
                    databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds, VarselType.AKTIVITETSKRAV)

                    log.info("-$name-: Lagrer ny varsel etter sletting med dato: -$aktivitetskravVarselDate-. -FOM, TOM: , $fom, $tom-")
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
                    log.info("-$name-: Lagrer ny varsel med dato: -$aktivitetskravVarselDate-. -FOM, TOM: , $fom, $tom-")
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
                log.info("-$name-: Lagrer ny varsel med dato: -$aktivitetskravVarselDate-")

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
            log.info("-$name-: Ingen gyldigeSykmeldingTilfelledager. Planlegger ikke nytt varsel")
        }
    }
}
