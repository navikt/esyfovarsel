package no.nav.syfo.varsel

import io.ktor.util.*
import kotlinx.coroutines.coroutineScope
import no.nav.syfo.consumer.domain.OppfolgingstilfellePerson
import no.nav.syfo.consumer.domain.SyketilfellebitTag
import no.nav.syfo.consumer.domain.Syketilfelledag
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deletePlanlagtVarselBySykmeldingerId
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.util.SykeforlopService
import no.nav.syfo.util.VarselUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit
import kotlin.streams.toList

@KtorExperimentalAPI
class AktivitetskravVarselPlanner(
    private val databaseAccess: DatabaseInterface,
    val sykmeldingService: SykmeldingService
) : VarselPlanner {
    private val AKTIVITETSKRAV_DAGER: Long = 42

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.AktivitetskravVarselPlanner")
    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)
    private val sykeforlopService: SykeforlopService = SykeforlopService()

    override suspend fun processOppfolgingstilfelle(oppfolgingstilfellePerson: OppfolgingstilfellePerson, fnr: String) = coroutineScope {
        log.info("[AKTIVITETSKRAV_VARSEL]: oppfolgingstilfellePerson:  $oppfolgingstilfellePerson")

        val gyldigeSykmeldingTifelledager = oppfolgingstilfellePerson.tidslinje.stream()
            .filter { isGyldigSykmeldingTilfelle(it) }
            .toList()
        log.info("[AKTIVITETSKRAV_VARSEL]: gyldigeSykmeldingTifelledager:  $gyldigeSykmeldingTifelledager")

        val sykeforloper = sykeforlopService.getSykeforloper(gyldigeSykmeldingTifelledager)

        for (sykeforlop in sykeforloper) {
            val forlopStartDato = sykeforlop.fom
            val forlopSluttDato = sykeforlop.tom

            val aktivitetskravVarselDato = forlopStartDato.plusDays(AKTIVITETSKRAV_DAGER)

            val forlopetsLengde = ChronoUnit.DAYS.between(forlopStartDato, forlopSluttDato)
            log.info("[AKTIVITETSKRAV_VARSEL]: forlopStartDato:  $forlopStartDato")
            log.info("[AKTIVITETSKRAV_VARSEL]: forlopSluttDato:  $forlopSluttDato")
            log.info("[AKTIVITETSKRAV_VARSEL]: forlopetsLengde:  $forlopetsLengde")
            log.info("[AKTIVITETSKRAV_VARSEL]: aktivitetskravVarselDato:  $aktivitetskravVarselDato")

            when {
                varselUtil.isVarselDatoForIDag(aktivitetskravVarselDato) -> {
                    log.info("[AKTIVITETSKRAV_VARSEL]: Beregnet dato for varsel er før i dag")
                }
                varselUtil.isVarselDatoEtterTilfelleSlutt(aktivitetskravVarselDato, forlopSluttDato) -> {
                    log.info("[AKTIVITETSKRAV_VARSEL]: Tilfelle er kortere enn 6 uker, sletter tidligere planlagt varsel om det finnes i DB")
                    databaseAccess.deletePlanlagtVarselBySykmeldingerId(sykeforlop.ressursIds)
                }
//TODO                    sykmeldingService.isNot100SykmeldtPaVarlingsdato(aktivitetskravVarselDato, fnr) -> {
//                        log.info("[AKTIVITETSKRAV_VARSEL]: Sykmeldingsgrad er < enn 100% på beregnet varslingsdato")
//                    }
                varselUtil.isVarselPlanlagt(fnr, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato) -> {
                    log.info("[AKTIVITETSKRAV_VARSEL]: varsel er allerede planlagt")
                }
                varselUtil.isVarselSendUt(fnr, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato) -> {
                    log.info("[AKTIVITETSKRAV_VARSEL]: varlel var allerede sendt ut")
                }
                else -> {
                    log.info("[AKTIVITETSKRAV_VARSEL]: Lagrer varsel til database")
                    val aktivitetskravVarsel = PlanlagtVarsel(fnr, oppfolgingstilfellePerson.aktorId, sykeforlop.ressursIds, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato)

                    databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                }
            }
        }
    }

    private fun isGyldigSykmeldingTilfelle(syketilfelledag: Syketilfelledag): Boolean {
        return (syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.SYKMELDING.name) == true
                || syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.PAPIRSYKMELDING.name) == true)
                && syketilfelledag.prioritertSyketilfellebit.tags.contains(SyketilfellebitTag.SENDT.name)
    }
}
