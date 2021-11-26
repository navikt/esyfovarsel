package no.nav.syfo.varsel

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
import no.nav.syfo.service.SykeforlopService
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.utils.VarselUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.streams.toList

class AktivitetskravVarselPlanner(
    private val databaseAccess: DatabaseInterface,
    private val syfosyketilfelleConsumer: SyfosyketilfelleConsumer,
    val sykmeldingService: SykmeldingService,
    override val name: String = "AKTIVITETSKRAV_VARSEL"
) : VarselPlanner {
    private val AKTIVITETSKRAV_DAGER: Long = 42

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.AktivitetskravVarselPlanner")
    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)
    private val sykeforlopService: SykeforlopService = SykeforlopService()

    override suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String) = coroutineScope {
        val oppfolgingstilfellePerson = syfosyketilfelleConsumer.getOppfolgingstilfelle(aktorId)

        if(oppfolgingstilfellePerson == null) {
            log.info("[$name]: Fant ikke oppfolgingstilfelle for denne brukeren. Planlegger ikke nytt varsel")
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
                    log.info("[$name]: Beregnet dato for varsel er før i dag, sletter tidligere planlagt varsel om det finnes i DB")
                    databaseAccess.deletePlanlagtVarselBySykmeldingerId(sykeforlop.ressursIds)
                } else if (varselUtil.isVarselDatoEtterTilfelleSlutt(aktivitetskravVarselDato, forlopSluttDato)) {
                    log.info("[$name]: Tilfelle er kortere enn 6 uker, sletter tidligere planlagt varsel om det finnes i DB")
                    databaseAccess.deletePlanlagtVarselBySykmeldingerId(sykeforlop.ressursIds)
                } else if (sykmeldingService.isNot100SykmeldtPaVarlingsdato(aktivitetskravVarselDato, fnr) == true){
                    log.info("[$name]: Sykmeldingsgrad er < enn 100% på beregnet varslingsdato, sletter tidligere planlagt varsel om det finnes i DB")
                    databaseAccess.deletePlanlagtVarselBySykmeldingerId(sykeforlop.ressursIds)
                } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDato }.isNotEmpty()){
                    log.info("[$name]: varsel med samme utsendingsdato er allerede planlagt")
                } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDato }.isEmpty()){
                    log.info("[$name]: sjekker om det finnes varsler med samme id")
                    if (varselUtil.hasLagreteVarslerForForespurteSykmeldinger(lagreteVarsler, sykeforlop.ressursIds)) {
                        log.info("[$name]: sletter tidligere varsler")
                        databaseAccess.deletePlanlagtVarselBySykmeldingerId(sykeforlop.ressursIds)

                        log.info("[$name]: Lagrer ny varsel")
                        val aktivitetskravVarsel = PlanlagtVarsel(fnr, oppfolgingstilfellePerson.aktorId, sykeforlop.ressursIds, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato)
                        databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                        break@loop
                    } else {
                        log.info("[$name]: Lagrer varsel til database")

                        val aktivitetskravVarsel = PlanlagtVarsel(fnr, oppfolgingstilfellePerson.aktorId, sykeforlop.ressursIds, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato)
                        databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                        tellAktivitetskravPlanlagt()
                        break@loop
                    }
                } else {
                    log.info("[$name]: Lagrer varsel til database")

                    val aktivitetskravVarsel = PlanlagtVarsel(fnr, oppfolgingstilfellePerson.aktorId, sykeforlop.ressursIds, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato)
                    databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                    tellAktivitetskravPlanlagt()
                }
            }
        } else {
            log.info("[$name]: Sykeforløpliste er tom")
        }
    }

    private fun isGyldigSykmeldingTilfelle(syketilfelledag: Syketilfelledag): Boolean {
        return (syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.SYKMELDING.name) == true
                || syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.PAPIRSYKMELDING.name) == true)
                && syketilfelledag.prioritertSyketilfellebit.tags.contains(SyketilfellebitTag.SENDT.name)
    }
}
