package no.nav.syfo.varsel

import kotlinx.coroutines.coroutineScope
import no.nav.syfo.consumer.NarmesteLederConsumer
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
import no.nav.syfo.utils.isEqualOrAfter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit

class AktivitetskravVarselPlanner(
    private val databaseAccess: DatabaseInterface,
    private val syfosyketilfelleConsumer: SyfosyketilfelleConsumer,
    private val narmesteLederConsumer: NarmesteLederConsumer,
    val sykmeldingService: SykmeldingService,
    override val name: String = "AKTIVITETSKRAV_VARSEL"
) : VarselPlanner {
    private val AKTIVITETSKRAV_DAGER: Long = 42

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.AktivitetskravVarselPlanner")
    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)

    override suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String) = coroutineScope {
        val oppfolgingstilfellePerson = syfosyketilfelleConsumer.getOppfolgingstilfelle(aktorId)
        log.info("-$name-: oppfolgingstilfellePerson er -$oppfolgingstilfellePerson-")

        if (oppfolgingstilfellePerson == null) {
            log.info("-$name-: Fant ikke oppfolgingstilfelle. Planlegger ikke nytt varsel")
            return@coroutineScope
        }

        val validSyketilfelledager = oppfolgingstilfellePerson.tidslinje.filter { isValidSyketilfelledag(it) }.sortedBy { it.dag }

        log.info("-$name-: gyldigeSyketilfelledager i tidslinjen er -$validSyketilfelledager-")

        if (validSyketilfelledager.isNotEmpty() && calculateActualNumberOfDaysInTimeline(validSyketilfelledager) >= AKTIVITETSKRAV_DAGER) {
            val newestSyketilfelledag = validSyketilfelledager.last()
            val oldestSyketilfelledag = validSyketilfelledager.first()

            val fom = oldestSyketilfelledag.prioritertSyketilfellebit!!.fom.toLocalDate()
            val tom = newestSyketilfelledag.prioritertSyketilfellebit!!.tom.toLocalDate()

            val aktivitetskravVarselDate = fom.plusDays(AKTIVITETSKRAV_DAGER)

            log.info("-$name-: oppfolgingstilfellePerson.fom er -$fom-")
            log.info("-$name-: oppfolgingstilfellePerson.tom er -$tom-")

            var ressursIds: MutableSet<String> = HashSet()

            validSyketilfelledager.forEach {
                ressursIds.add(it.prioritertSyketilfellebit!!.ressursId)
            }

            log.info("-$name-: nyestOppT: $newestSyketilfelledag")
            log.info("-$name-: relevante -FOM, TOM, DATO, RESSURS_IDS: $fom, $tom, $aktivitetskravVarselDate, $ressursIds-")

            val lagreteVarsler = varselUtil.getPlanlagteVarslerAvType(fnr, VarselType.AKTIVITETSKRAV)

            if (varselUtil.isVarselDatoForIDag(aktivitetskravVarselDate)) {
                log.info("-$name-: Beregnet dato for varsel er før i dag, sletter tidligere planlagt varsel om det finnes i DB. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds)
            } else if (varselUtil.isVarselDatoEtterTilfelleSlutt(aktivitetskravVarselDate, tom)) {
                log.info("-$name-: Tilfelle er kortere enn 6 uker, sletter tidligere planlagt varsel om det finnes i DB. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds)
            } else if (sykmeldingService.isNot100SykmeldtPaVarlingsdato(aktivitetskravVarselDate, fnr) == true) {
                log.info("-$name-: Sykmeldingsgrad er < enn 100% på beregnet varslingsdato, sletter tidligere planlagt varsel om det finnes i DB. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds)
            } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDate }
                    .isNotEmpty()) {
                log.info("-$name-: varsel med samme utsendingsdato er allerede planlagt. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
            } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == aktivitetskravVarselDate }
                    .isEmpty()) {
                log.info("-$name-: sjekker om det finnes varsler med samme id. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
                if (varselUtil.hasLagreteVarslerForForespurteSykmeldinger(lagreteVarsler, ressursIds)) {
                    log.info("-$name-: sletter tidligere varsler for. -FOM, TOM, DATO: , $fom, $tom, $aktivitetskravVarselDate-")
                    databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds)

                    log.info("-$name-: Lagrer ny varsel etter sletting med dato: -$aktivitetskravVarselDate-. -FOM, TOM: , $fom, $tom-")
                    val aktivitetskravVarsel = PlanlagtVarsel(
                        fnr,
                        oppfolgingstilfellePerson.aktorId,
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
                    ressursIds,
                    VarselType.AKTIVITETSKRAV,
                    aktivitetskravVarselDate
                )
                databaseAccess.storePlanlagtVarsel(aktivitetskravVarsel)
                tellAktivitetskravPlanlagt()
            }
        }
        log.info("-$name-: Ingen gyldigeSykmeldingTilfelledager. Planlegger ikke nytt varsel")
    }

    private fun isValidSyketilfelledag(syketilfelledag: Syketilfelledag): Boolean {
        val hasValidDocumentType = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.SYKMELDING.name) == true ||
                syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.PAPIRSYKMELDING.name) == true ||
                syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.SYKEPENGESOKNAD.name) == true

        val isAcceptedDocument = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.SENDT.name) == true ||
                syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.BEKREFTET.name) == true

        val isBehandlingsdag = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.BEHANDLINGSDAGER.name) == true

        val isFravarForSykmelding = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SyketilfellebitTag.FRAVAR_FOR_SYKMELDING.name) == true

        return hasValidDocumentType && isAcceptedDocument && !isBehandlingsdag && !isFravarForSykmelding
    }

    fun calculateActualNumberOfDaysInTimeline(validSyketilfelledager: List<Syketilfelledag>): Int {
        val first = validSyketilfelledager[0].prioritertSyketilfellebit
        var actualNumberOfDaysInTimeline =  ChronoUnit.DAYS.between(first!!.fom, first.tom).toInt()

        for (i in 1 until validSyketilfelledager.size) {
            val currentFom = validSyketilfelledager[i].prioritertSyketilfellebit!!.fom.toLocalDate()
            val currentTom = validSyketilfelledager[i].prioritertSyketilfellebit!!.tom.toLocalDate()
            val previousTom = validSyketilfelledager[i - 1].prioritertSyketilfellebit!!.tom.toLocalDate()

            if (currentFom.isEqualOrAfter(previousTom)) {
                val currentLength = ChronoUnit.DAYS.between(currentFom, currentTom).toInt()
                actualNumberOfDaysInTimeline += currentLength
            }
        }
        return actualNumberOfDaysInTimeline
    }
}
