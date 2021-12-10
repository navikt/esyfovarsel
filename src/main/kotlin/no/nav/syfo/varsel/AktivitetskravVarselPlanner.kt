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

    override suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String) = coroutineScope {
        val oppfolgingstilfellePerson = syfosyketilfelleConsumer.getOppfolgingstilfelle(aktorId)

        if (oppfolgingstilfellePerson == null) {
            log.info("-$name-: Fant ikke oppfolgingstilfelle for -$aktorId- aktor id. Planlegger ikke nytt varsel")
            return@coroutineScope
        }

        val gyldigeSykmeldingTilfelledager = oppfolgingstilfellePerson.tidslinje.stream()
            .filter { isGyldigSykmeldingTilfelle(it) }
            .toList()

        log.info("-$name-: gyldigeSykmeldingTilfelledager i tidslinjen for -$aktorId- aktor id er -$gyldigeSykmeldingTilfelledager-")

        val nyestOppT = gyldigeSykmeldingTilfelledager.lastOrNull()
        val eldsteOppT = gyldigeSykmeldingTilfelledager.firstOrNull()

        val fom = eldsteOppT!!.dag
        val tom = nyestOppT!!.dag
        val aktivitetskravVarselDato = fom.plusDays(AKTIVITETSKRAV_DAGER)
        log.info("-$name-: oppfolgingstilfellePerson.fom for -$aktorId- aktor id er -$fom-")
        log.info("-$name-: oppfolgingstilfellePerson.tom for -$aktorId- aktor id er -$tom-")

        val ressursIds: Set<String> = mutableSetOf()
        gyldigeSykmeldingTilfelledager.forEach {ressursIds.add(it.prioritertSyketilfellebit!!.ressursId!!) }
        val lagreteVarsler = varselUtil.getPlanlagteVarslerAvType(fnr, VarselType.AKTIVITETSKRAV)

        if (varselUtil.isVarselDatoForIDag(aktivitetskravVarselDato)) {
            log.info("-$name-: Beregnet dato for varsel for -$aktorId- aktor id er før i dag, sletter tidligere planlagt varsel om det finnes i DB")
            databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds)
        } else if (varselUtil.isVarselDatoEtterTilfelleSlutt(aktivitetskravVarselDato, tom)) {
            log.info("-$name-: Tilfelle er kortere enn 6 uker, sletter tidligere planlagt varsel for -$aktorId- aktor id om det finnes i DB")
            databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds)
        } else if (sykmeldingService.isNot100SykmeldtPaVarlingsdato(aktivitetskravVarselDato, fnr) == true) {
            log.info("-$name-: Sykmeldingsgrad er < enn 100% på beregnet varslingsdato, sletter tidligere planlagt varsel for -$aktorId- aktor id om det finnes i DB")
            databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds)
        } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDato }.isNotEmpty()) {
            log.info("-$name-: varsel med samme utsendingsdato er allerede planlagt for -$aktorId- aktor id")
        } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDato }.isEmpty())  {
            log.info("-$name-: sjekker om det finnes varsler med samme id for -$aktorId- aktor id")
            if (varselUtil.hasLagreteVarslerForForespurteSykmeldinger(lagreteVarsler, ressursIds)) {
                log.info("-$name-: sletter tidligere varsler for -$aktorId- aktor id")
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds)

                log.info("-$name-: Lagrer ny varsel etter sletting for -$aktorId- aktor id med dato: -$aktivitetskravVarselDato-")
                val aktivitetskravVarsel = PlanlagtVarsel(fnr, oppfolgingstilfellePerson.aktorId, ressursIds, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato)
                databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
            } else {
                log.info("-$name-: Lagrer ny varsel for -$aktorId- aktor id med dato: -$aktivitetskravVarselDato-")
                val aktivitetskravVarsel = PlanlagtVarsel(fnr, oppfolgingstilfellePerson.aktorId, ressursIds, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato)
                databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                tellAktivitetskravPlanlagt()
            }
        } else {
            log.info("-$name-: Lagrer ny varsel for -$aktorId- aktor id med dato: -$aktivitetskravVarselDato-")

            val aktivitetskravVarsel = PlanlagtVarsel(fnr, oppfolgingstilfellePerson.aktorId, ressursIds, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato)
            databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
            tellAktivitetskravPlanlagt()
        }
    }

    private fun isGyldigSykmeldingTilfelle(syketilfelledag: Syketilfelledag): Boolean {
        return (syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.SYKMELDING.name) == true
                || syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.PAPIRSYKMELDING.name) == true)
                && syketilfelledag.prioritertSyketilfellebit.tags.contains(SyketilfellebitTag.SENDT.name)
    }
}
