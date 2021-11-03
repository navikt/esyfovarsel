package no.nav.syfo.varsel

import io.ktor.util.*
import kotlinx.coroutines.coroutineScope
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.consumer.domain.SyketilfellebitTag
import no.nav.syfo.consumer.domain.Syketilfelledag
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deletePlanlagtVarselBySykmeldingerId
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.metrics.tellAktivitetskravPlanlagt
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.service.SykeforlopService
import no.nav.syfo.utils.VarselUtil
import kotlin.streams.toList

@KtorExperimentalAPI
class AktivitetskravVarselPlanner(
    private val databaseAccess: DatabaseInterface,
    private val syfosyketilfelleConsumer: SyfosyketilfelleConsumer,
    val sykmeldingService: SykmeldingService,
    override val name: String = "AKTIVITETSKRAV_VARSEL"
) : VarselPlanner {
    private val AKTIVITETSKRAV_DAGER: Long = 42

    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)
    private val sykeforlopService: SykeforlopService = SykeforlopService()

    override suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String) = coroutineScope {
        val oppfolgingstilfellePerson = syfosyketilfelleConsumer.getOppfolgingstilfelle(aktorId)

        if(oppfolgingstilfellePerson == null) {
            return@coroutineScope
        }

        val gyldigeSykmeldingTilfelledager = oppfolgingstilfellePerson.tidslinje.stream()
            .filter { isGyldigSykmeldingTilfelle(it) }
            .toList()

        val sykeforlopList = sykeforlopService.getSykeforlopList(gyldigeSykmeldingTilfelledager)

        if (sykeforlopList.isNotEmpty()) {
            loop@ for (sykeforlop in sykeforlopList) {
                val forlopStartDato = sykeforlop.fom
                val forlopSluttDato = sykeforlop.tom

                val aktivitetskravVarselDato = forlopStartDato.plusDays(AKTIVITETSKRAV_DAGER)
                val lagreteVarsler = varselUtil.getPlanlagteVarslerAvType(fnr, VarselType.AKTIVITETSKRAV)

                if (varselUtil.isVarselDatoForIDag(aktivitetskravVarselDato)) {
                    databaseAccess.deletePlanlagtVarselBySykmeldingerId(sykeforlop.ressursIds)
                } else if (varselUtil.isVarselDatoEtterTilfelleSlutt(aktivitetskravVarselDato, forlopSluttDato)) {
                    databaseAccess.deletePlanlagtVarselBySykmeldingerId(sykeforlop.ressursIds)
                } else if (sykmeldingService.isNot100SykmeldtPaVarlingsdato(aktivitetskravVarselDato, fnr) == true){
                    databaseAccess.deletePlanlagtVarselBySykmeldingerId(sykeforlop.ressursIds)
                } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDato }.isNotEmpty()){
                } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDato }.isEmpty()){
                    if (varselUtil.hasLagreteVarslerForForespurteSykmeldinger(lagreteVarsler, sykeforlop.ressursIds)) {
                        databaseAccess.deletePlanlagtVarselBySykmeldingerId(sykeforlop.ressursIds)

                        val aktivitetskravVarsel = PlanlagtVarsel(fnr, oppfolgingstilfellePerson.aktorId, sykeforlop.ressursIds, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato)
                        databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                        break@loop
                    } else {

                        val aktivitetskravVarsel = PlanlagtVarsel(fnr, oppfolgingstilfellePerson.aktorId, sykeforlop.ressursIds, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato)
                        databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                        tellAktivitetskravPlanlagt()
                        break@loop
                    }
                } else {

                    val aktivitetskravVarsel = PlanlagtVarsel(fnr, oppfolgingstilfellePerson.aktorId, sykeforlop.ressursIds, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato)
                    databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                    tellAktivitetskravPlanlagt()
                }
            }
        } else {
        }
    }

    private fun isGyldigSykmeldingTilfelle(syketilfelledag: Syketilfelledag): Boolean {
        return (syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.SYKMELDING.name) == true
                || syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.PAPIRSYKMELDING.name) == true)
                && syketilfelledag.prioritertSyketilfellebit.tags.contains(SyketilfellebitTag.SENDT.name)
    }
}
