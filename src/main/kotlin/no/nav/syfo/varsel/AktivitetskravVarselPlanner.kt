package no.nav.syfo.varsel

import io.ktor.util.*
import kotlinx.coroutines.coroutineScope
import no.nav.syfo.consumer.domain.*
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deletePlanlagtVarselBySykmeldingerId
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.service.SykmeldingService
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
    private val SYKEFORLOP_MIN_DIFF_DAGER: Long = 16

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.AktivitetskravVarselPlanner")
    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)

    override suspend fun processOppfolgingstilfelle(oppfolgingstilfellePerson: OppfolgingstilfellePerson, fnr: String) = coroutineScope {
        log.info("[AKTIVITETSKRAV_VARSEL]: oppfolgingstilfellePerson:  $oppfolgingstilfellePerson")
        val sisteSyketilfelledagObject = oppfolgingstilfellePerson.tidslinje.last()

        if (isGyldigSykmeldingTilfelle(sisteSyketilfelledagObject)) {
            val sisteRessursId = sisteSyketilfelledagObject.prioritertSyketilfellebit?.ressursId

            val gyldigeSykmeldingTifelledager = oppfolgingstilfellePerson.tidslinje.stream()
                .filter { isGyldigSykmeldingTilfelle(it) }
                .toList()
            log.info("[AKTIVITETSKRAV_VARSEL]: gyldigeSykmeldingTifelledager:  $gyldigeSykmeldingTifelledager")

            val gjeldendeSykmeldingtilfelle = gyldigeSykmeldingTifelledager.stream()
                .filter { it.prioritertSyketilfellebit?.ressursId == sisteRessursId }
                .findAny()
                .get()
            log.info("[AKTIVITETSKRAV_VARSEL]: gjeldendeSykmeldingtilfelle:  $gjeldendeSykmeldingtilfelle")

            val grupperteSykmeldingTifelledager = groupByRessursId(gyldigeSykmeldingTifelledager)
            log.info("[AKTIVITETSKRAV_VARSEL]: grupperteSykmeldingTifelledager:  $grupperteSykmeldingTifelledager")

            val sykeforloper = getSykeforloper(grupperteSykmeldingTifelledager)
            log.info("[AKTIVITETSKRAV_VARSEL]: sykeforloper:  $sykeforloper")

            val sykeforlopOptional = sykeforloper.stream()
                .filter { isSyketilfelledagInnenSykeforlop(gjeldendeSykmeldingtilfelle, it) }
                .findAny()
            log.info("[AKTIVITETSKRAV_VARSEL]: sykeforlopOptional:  $sykeforlopOptional")

            if (sykeforlopOptional.isPresent) {
                val sykeforlop = sykeforlopOptional.get()
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
//                    sykmeldingService.isNot100SykmeldtPaVarlingsdato(aktivitetskravVarselDato, fnr) -> {
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
            } else {
                log.info("[AKTIVITETSKRAV_VARSEL]: Kunne ikke lage sykeforlop")
            }
        }
    }

    private fun isGyldigSykmeldingTilfelle(syketilfelledag: Syketilfelledag): Boolean {
        return (syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.SYKMELDING.name) == true
                || syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.PAPIRSYKMELDING.name) == true)
                && syketilfelledag.prioritertSyketilfellebit.tags.contains(SyketilfellebitTag.SENDT.name)
    }

    private fun groupByRessursId(syketilfelledager: List<Syketilfelledag>): List<Sykmeldingtilfelle> {
        val sykmeldingtilfeller: MutableList<Sykmeldingtilfelle> = mutableListOf()
        val ressursIds: Set<String?> = syketilfelledager.map { i -> i.prioritertSyketilfellebit?.ressursId }.toSet()

        ressursIds.forEach { it ->
            val id = it
            val biter = syketilfelledager.filter { it.prioritertSyketilfellebit?.ressursId == id }
                .map { i -> i.prioritertSyketilfellebit }

            biter.stream().map { bit ->
                sykmeldingtilfeller.add(Sykmeldingtilfelle(id!!, bit!!.fom.toLocalDate(), bit.tom.toLocalDate()))
            }.toList().toMutableList()
        }
        log.info("[AKTIVITETSKRAV_VARSEL]: Laget sykmeldingtilfeller:  $sykmeldingtilfeller")
        return sykmeldingtilfeller
    }

    private fun getSykeforloper(sykmeldingtilfeller: List<Sykmeldingtilfelle>): List<Sykeforlop> {
        val sykeforloper: MutableList<Sykeforlop> = mutableListOf()

        var prevTilf: Sykmeldingtilfelle = sykmeldingtilfeller[0]
        var sykeforlop = Sykeforlop(mutableListOf(prevTilf.ressursId), prevTilf.fom, prevTilf.tom)

        for (i in 1..sykmeldingtilfeller.size - 1) {
            val currTilf: Sykmeldingtilfelle = sykmeldingtilfeller[i]
            if (ChronoUnit.DAYS.between(prevTilf.tom, currTilf.fom) <= SYKEFORLOP_MIN_DIFF_DAGER) {
                sykeforlop.ressursIds.add(currTilf.ressursId)
                sykeforlop.tom = currTilf.tom
            } else {
                sykeforloper.add(sykeforlop)
                sykeforlop = Sykeforlop(mutableListOf(currTilf.ressursId), currTilf.fom, currTilf.tom)
            }
            prevTilf = currTilf
        }
        sykeforloper.add(sykeforlop)
        log.info("[AKTIVITETSKRAV_VARSEL]: Laget sykeforloper:  $sykeforloper")
        return sykeforloper
    }

    private fun isSyketilfelledagInnenSykeforlop(syketilfelledag: Syketilfelledag, sykeforlop: Sykeforlop): Boolean {
        return syketilfelledag.prioritertSyketilfellebit!!.fom.toLocalDate().isEqual(sykeforlop.fom)
                || syketilfelledag.prioritertSyketilfellebit.tom.toLocalDate().isEqual(sykeforlop.tom)
                || syketilfelledag.prioritertSyketilfellebit.fom.toLocalDate().isAfter(sykeforlop.fom) && syketilfelledag.prioritertSyketilfellebit.tom.toLocalDate().isBefore(sykeforlop.tom)
    }
}
