package no.nav.syfo.varsel

import io.ktor.util.*
import kotlinx.coroutines.coroutineScope
import no.nav.syfo.consumer.domain.*
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.util.VarselUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit
import kotlin.streams.toList

class AktivitetskravVarselPlanner(val databaseAccess: DatabaseInterface, val sykmeldingService: SykmeldingService) : VarselPlanner {

    private val AKTIVITETSKRAV_DAGER: Long = 42;
    private val SYKEFORLOP_MIN_DIFF_DAGER: Long = 16;

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.AktivitetskravVarselPlanner")
    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)

    @KtorExperimentalAPI
    override suspend fun processOppfolgingstilfelle(oppfolgingstilfellePerson: OppfolgingstilfellePerson) = coroutineScope {
        //TODO: implement pdl getFnrForAktorId
        val arbeidstakerFnr = "07088621268"

        val sisteSyketilfelledagObject = oppfolgingstilfellePerson.tidslinje.last()

        if (isGyldigSykmeldingTilfelle(sisteSyketilfelledagObject)) {
            val sisteRessursId = sisteSyketilfelledagObject.prioritertSyketilfellebit?.ressursId

            val gyldigeSykmeldingTifelledager = oppfolgingstilfellePerson.tidslinje.stream()
                .filter { isGyldigSykmeldingTilfelle(it) }
                .toList()

            val gjeldendeSykmeldingtilfelle = gyldigeSykmeldingTifelledager.stream()
                .filter { it.prioritertSyketilfellebit?.ressursId == sisteRessursId }
                .findAny()
                .get()

            val grupperteSykmeldingTifelledager = groupByRessursId(gyldigeSykmeldingTifelledager)
            val sykeforloper = getSykeforloper(grupperteSykmeldingTifelledager)

            val sykeforlopOptional = sykeforloper.stream()
                .filter { isSyketilfelledagInnenSykeforlop(gjeldendeSykmeldingtilfelle, it) }
                .findAny()

            if (sykeforlopOptional.isPresent) {
                val sykeforlop = sykeforlopOptional.get()
                val forlopStartDato = sykeforlop.fom
                val forlopSluttDato = sykeforlop.tom

                val aktivitetskravVarselDato = forlopStartDato.plusDays(AKTIVITETSKRAV_DAGER)

                when {
                    varselUtil.isVarselDatoForIDag(aktivitetskravVarselDato) -> {
                        log.info("[AKTIVITETSKRAV_VARSEL]: Beregnet dato for varsel er før i dag")
                    }
                    varselUtil.isVarselDatoEtterTilfelleSlutt(aktivitetskravVarselDato, forlopSluttDato) -> {
                        log.info("[AKTIVITETSKRAV_VARSEL]: Tilfelle er kortere enn 6 uker")
                    }
                    sykmeldingService.isNot100SykmeldtPaVarlingsdato(aktivitetskravVarselDato, arbeidstakerFnr) -> {
                        log.info("[AKTIVITETSKRAV_VARSEL]: Sykmeldingsgrad er < enn 100% på beregnet varslingsdato")
                    }
                    varselUtil.isVarselPlanlagt(arbeidstakerFnr, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato) -> {
                        log.info("[AKTIVITETSKRAV_VARSEL]: varlel er allerede planlagt")
                    }
                    varselUtil.isVarselSendUt(arbeidstakerFnr, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato) -> {
                        log.info("[AKTIVITETSKRAV_VARSEL]: varlel var allerede sendt ut")
                    }
                    else -> {
                        log.info("[AKTIVITETSKRAV_VARSEL]: Lagrer varsel til database")
                        val aktivitetskravVarsel = PlanlagtVarsel(arbeidstakerFnr, oppfolgingstilfellePerson.aktorId, VarselType.AKTIVITETSKRAV, aktivitetskravVarselDato)

                        databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                    }
                }
            } else {
                log.info("[AKTIVITETSKRAV_VARSEL]: Tifellestart eller tilfelleslutt  dato er tom")
            }
        }
    }

    private fun isGyldigSykmeldingTilfelle(syketilfelledag: Syketilfelledag): Boolean {
        return (syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.SYKMELDING.name) == true
                || syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.PAPIRSYKMELDING.name) == true)
                && syketilfelledag.prioritertSyketilfellebit.tags.contains(SyketilfellebitTag.SENDT.name)
    }

    private fun groupByRessursId(syketilfelledager: List<Syketilfelledag>): List<Sykmeldingtilfelle> {
        var sykmeldingtilfeller: MutableList<Sykmeldingtilfelle> = mutableListOf()
        val ressursIds: Set<String?> = syketilfelledager.map { i -> i.prioritertSyketilfellebit?.ressursId }.toSet()

        ressursIds.forEach {
            val id = it
            val biter = syketilfelledager.filter { it.prioritertSyketilfellebit?.ressursId == id }
                .map { i -> i.prioritertSyketilfellebit }

            sykmeldingtilfeller = biter.stream().map { it1 ->
                Sykmeldingtilfelle(id!!, it1!!.fom.toLocalDate(), it1.tom.toLocalDate())
            }.toList().toMutableList()
        }
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
        return sykeforloper
    }

    private fun isSyketilfelledagInnenSykeforlop(syketilfelledag: Syketilfelledag, sykeforlop: Sykeforlop): Boolean {
        return syketilfelledag.dag.isAfter(sykeforlop.fom) && syketilfelledag.dag.isBefore(sykeforlop.fom) || syketilfelledag.dag.isEqual(sykeforlop.fom) || syketilfelledag.dag.isEqual(sykeforlop.tom)
    }
}
