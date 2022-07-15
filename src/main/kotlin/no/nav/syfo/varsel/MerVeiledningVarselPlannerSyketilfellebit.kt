package no.nav.syfo.varsel

import kotlinx.coroutines.coroutineScope
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deletePlanlagtVarselByVarselId
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.db.updateUtsendingsdatoByVarselId
import no.nav.syfo.metrics.tellMerVeiledningPlanlagt
import no.nav.syfo.service.VarselSendtService
import no.nav.syfo.syketilfelle.SyketilfellebitService
import no.nav.syfo.utils.VarselUtil
import no.nav.syfo.utils.todayIsBetweenFomAndTom
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MerVeiledningVarselPlannerSyketilfellebit(
    val databaseAccess: DatabaseInterface,
    val syketilfellebitService: SyketilfellebitService,
    val varselSendtService: VarselSendtService
) : VarselPlannerSyketilfellebit {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.MerVeiledningVarselPlannerSyketilfellebit")
    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)
    override val name: String = "MER_VEILEDNING_VARSEL_GCP"

    override suspend fun processSyketilfelle(fnr: String, orgnummer: String) = coroutineScope {
        val oppfolgingstilfelle = syketilfellebitService.beregnKOppfolgingstilfelle39UkersVarsel(fnr) ?: return@coroutineScope

        val tilfelleFom = oppfolgingstilfelle.fom
        val tilfelleTom = oppfolgingstilfelle.tom

        if (todayIsBetweenFomAndTom(tilfelleFom, tilfelleTom)) {
            val varselDato = varselUtil.varselDate39Uker(oppfolgingstilfelle)

            if (varselDato == null) {
                val tidligerePlanlagteVarslerPaFnr = varselUtil.getPlanlagteVarslerAvType(fnr, VarselType.MER_VEILEDNING)

                if (tidligerePlanlagteVarslerPaFnr.isNotEmpty()) {
                    val sistePlanlagteVarsel = tidligerePlanlagteVarslerPaFnr.first()
                    databaseAccess.deletePlanlagtVarselByVarselId(sistePlanlagteVarsel.uuid)
                    log.info("[$name]: Antall dager utbetalt er færre enn 39 uker tilsammen i sykefraværet. Sletter tidligere planlagt varsel.")
                } else {
                    log.info("[$name]: Antall dager utbetalt er færre enn 39 uker tilsammen i sykefraværet. Planlegger ikke varsel")
                }
            } else {
                log.info("[$name]: [FOM,TOM,ANT,DATO]: [$tilfelleFom,$tilfelleTom,${oppfolgingstilfelle.antallSykefravaersDagerTotalt},$varselDato]")

                val arbeidstakerAktorId = oppfolgingstilfelle.aktorId

                val varsel = PlanlagtVarsel(
                    fnr,
                    arbeidstakerAktorId,
                    orgnummer,
                    emptySet(),
                    VarselType.MER_VEILEDNING,
                    varselDato
                )

                if (varselSendtService.erVarselSendt(fnr, VarselType.MER_VEILEDNING, tilfelleFom, tilfelleTom)) {
                    log.info("[$name]: Varsel har allerede blitt sendt ut til bruker i dette sykeforløpet. Planlegger ikke nytt varsel")
                    return@coroutineScope
                }
                val tidligerePlanlagteVarslerPaFnr = varselUtil.getPlanlagteVarslerAvType(fnr, VarselType.MER_VEILEDNING)

                if (tidligerePlanlagteVarslerPaFnr.isNotEmpty()) {
                    val sisteUsendteVarsel = tidligerePlanlagteVarslerPaFnr.first()
                    databaseAccess.updateUtsendingsdatoByVarselId(sisteUsendteVarsel.uuid, varselDato)
                    log.info("[$name]: Oppdaterte tidligere usendt 39-ukers varsel i samme sykeforløp")
                } else {
                    log.info("[$name]: Planlegger 39-ukers varsel")
                    databaseAccess.storePlanlagtVarsel(varsel)
                    tellMerVeiledningPlanlagt()
                }
            }
        } else {
            log.info("[$name]: Dagens dato er utenfor [fom,tom] intervall til oppfølgingstilfelle. Planlegger ikke varsel")
        }
    }
}
