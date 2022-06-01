package no.nav.syfo.varsel

import kotlinx.coroutines.coroutineScope
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deletePlanlagtVarselBySykmeldingerId
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.Syketilfelledag
import no.nav.syfo.metrics.tellAktivitetskravPlanlagt
import no.nav.syfo.service.VarselSendtService
import no.nav.syfo.syketilfelle.domain.Tag.*
import no.nav.syfo.utils.VarselUtil
import no.nav.syfo.utils.isEqualOrAfter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit

class SvarMotebehovVarselPlanner(
    private val databaseAccess: DatabaseInterface,
    private val syfosyketilfelleConsumer: SyfosyketilfelleConsumer,
    private val varselSendtService: VarselSendtService,
    override val name: String = "SVAR_MOTEBEHOV_VARSEL"
) : VarselPlanner {
    private val SVAR_MOTEBEHOV_DAGER: Long = 112

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.SvarMotebehovVarselPlanner")
    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)

    override suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String, orgnummer: String?) = coroutineScope {
        val oppfolgingstilfellePerson = syfosyketilfelleConsumer.getOppfolgingstilfelle(aktorId)
        log.info("-$name-: oppfolgingstilfellePerson er -$oppfolgingstilfellePerson-")

        if (oppfolgingstilfellePerson == null) {
            log.info("-$name-: Fant ikke oppfolgingstilfelle. Planlegger ikke nytt varsel")
            return@coroutineScope
        }

        val validSyketilfelledager = oppfolgingstilfellePerson.tidslinje.filter { isValidSyketilfelledag(it) }.sortedBy { it.dag }

        log.info("-$name-: gyldigeSyketilfelledager i tidslinjen er -${validSyketilfelledager.size}-")
        validSyketilfelledager.forEach {
            log.info("-$name-: Dag ${it.dag}, fom ${it.prioritertSyketilfellebit?.fom}, tom ${it.prioritertSyketilfellebit?.tom} ")
        }

        if (validSyketilfelledager.isNotEmpty() && calculateActualNumberOfDaysInTimeline(validSyketilfelledager) >= SVAR_MOTEBEHOV_DAGER) {
            val newestSyketilfelledag = validSyketilfelledager.last()
            val oldestSyketilfelledag = validSyketilfelledager.first()

            val fom = oldestSyketilfelledag.prioritertSyketilfellebit!!.fom
            val tom = newestSyketilfelledag.prioritertSyketilfellebit!!.tom

            val svarMotebehovVarselDate = fom.plusDays(SVAR_MOTEBEHOV_DAGER)

            log.info("-$name-: oppfolgingstilfellePerson.fom er -$fom-")
            log.info("-$name-: oppfolgingstilfellePerson.tom er -$tom-")

            val ressursIds: MutableSet<String> = HashSet()

            validSyketilfelledager.forEach {
                ressursIds.add(it.prioritertSyketilfellebit!!.ressursId)
            }

            log.info("-$name-: nyestOppT: $newestSyketilfelledag")
            log.info("-$name-: relevante -FOM, TOM, DATO, RESSURS_IDS: $fom, $tom, $svarMotebehovVarselDate, $ressursIds-")

            val lagreteVarsler = varselUtil.getPlanlagteVarslerAvType(fnr, VarselType.SVAR_MOTEBEHOV)

            if (varselUtil.isVarselDatoForIDag(svarMotebehovVarselDate)) {
                log.info("-$name-: Beregnet dato for varsel er før i dag, sletter tidligere planlagt varsel om det finnes i DB. -FOM, TOM, DATO: , $fom, $tom, $svarMotebehovVarselDate-")
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds)
            } else if (varselUtil.isVarselDatoEtterTilfelleSlutt(svarMotebehovVarselDate, tom)) {
                log.info("-$name-: Tilfelle er kortere enn 112 dager, sletter tidligere planlagt varsel om det finnes i DB. -FOM, TOM, DATO: , $fom, $tom, $svarMotebehovVarselDate-")
                databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds)
            } else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == svarMotebehovVarselDate }
                    .isNotEmpty()) {
                log.info("-$name-: varsel med samme utsendingsdato er allerede planlagt. -FOM, TOM, DATO: , $fom, $tom, $svarMotebehovVarselDate-")
            } else if (varselSendtService.erVarselSendt(fnr, VarselType.SVAR_MOTEBEHOV, fom, tom)) {
                log.info("[$name]: Varsel har allerede blitt sendt ut til bruker i dette sykeforløpet. Planlegger ikke nytt varsel")
            }
            else if (lagreteVarsler.isNotEmpty() && lagreteVarsler.filter { it.utsendingsdato == svarMotebehovVarselDate }
                    .isEmpty()) {
                log.info("-$name-: sjekker om det finnes varsler med samme id. -FOM, TOM, DATO: , $fom, $tom, $svarMotebehovVarselDate-")
                if (varselUtil.hasLagreteVarslerForForespurteSykmeldinger(lagreteVarsler, ressursIds)) {
                    log.info("-$name-: sletter tidligere varsler for. -FOM, TOM, DATO: , $fom, $tom, $svarMotebehovVarselDate-")
                    databaseAccess.deletePlanlagtVarselBySykmeldingerId(ressursIds)

                    log.info("-$name-: Lagrer ny varsel etter sletting med dato: -$svarMotebehovVarselDate-. -FOM, TOM: , $fom, $tom-")
                    val svarMotebehovVarsel = PlanlagtVarsel(
                        fnr,
                        oppfolgingstilfellePerson.aktorId,
                        orgnummer,
                        ressursIds,
                        VarselType.SVAR_MOTEBEHOV,
                        svarMotebehovVarselDate
                    )
                    databaseAccess.storePlanlagtVarsel(svarMotebehovVarsel)
                } else {
                    log.info("-$name-: Lagrer ny varsel med dato: -$svarMotebehovVarselDate-. -FOM, TOM: , $fom, $tom-")
                    val svarMotebehovVarsel = PlanlagtVarsel(
                        fnr,
                        oppfolgingstilfellePerson.aktorId,
                        orgnummer,
                        ressursIds,
                        VarselType.SVAR_MOTEBEHOV,
                        svarMotebehovVarselDate
                    )
                    databaseAccess.storePlanlagtVarsel(svarMotebehovVarsel)
                    tellAktivitetskravPlanlagt()
                }
            } else {
                log.info("-$name-: Lagrer ny varsel med dato: -$svarMotebehovVarselDate-")

                val svarMotebehovVarsel = PlanlagtVarsel(
                    fnr,
                    oppfolgingstilfellePerson.aktorId,
                    orgnummer,
                    ressursIds,
                    VarselType.SVAR_MOTEBEHOV,
                    svarMotebehovVarselDate
                )
                databaseAccess.storePlanlagtVarsel(svarMotebehovVarsel)
                tellAktivitetskravPlanlagt()
            }
        } else {
            log.info("-$name-: Ingen gyldigeSykmeldingTilfelledager eller har ikke vært sykmeldt $SVAR_MOTEBEHOV_DAGER dager. Planlegger ikke nytt varsel")
        }
    }

    private fun isValidSyketilfelledag(syketilfelledag: Syketilfelledag): Boolean {
        val hasValidDocumentType = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SYKMELDING) == true ||
                syketilfelledag.prioritertSyketilfellebit?.tags?.contains(PAPIRSYKMELDING) == true ||
                syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SYKEPENGESOKNAD) == true

        val isAcceptedDocument = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(SENDT) == true ||
                syketilfelledag.prioritertSyketilfellebit?.tags?.contains(BEKREFTET) == true

        val isBehandlingsdag = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(BEHANDLINGSDAGER) == true

        val isFravarForSykmelding = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(FRAVAR_FOR_SYKMELDING) == true

        return hasValidDocumentType && isAcceptedDocument && !isBehandlingsdag && !isFravarForSykmelding
    }

    fun calculateActualNumberOfDaysInTimeline(validSyketilfelledager: List<Syketilfelledag>): Int {
        val first = validSyketilfelledager[0].prioritertSyketilfellebit
        var actualNumberOfDaysInTimeline =  ChronoUnit.DAYS.between(first!!.fom, first.tom).toInt()

        for (i in 1 until validSyketilfelledager.size) {
            val currentFom = validSyketilfelledager[i].prioritertSyketilfellebit!!.fom
            val currentTom = validSyketilfelledager[i].prioritertSyketilfellebit!!.tom
            val previousTom = validSyketilfelledager[i - 1].prioritertSyketilfellebit!!.tom

            if (currentFom.isEqualOrAfter(previousTom)) {
                val currentLength = ChronoUnit.DAYS.between(currentFom, currentTom).toInt()
                actualNumberOfDaysInTimeline += currentLength
            }
        }
        log.info("-$name-: Antall sykedager er $actualNumberOfDaysInTimeline")
        return actualNumberOfDaysInTimeline
    }
}
