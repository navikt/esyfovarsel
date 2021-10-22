package no.nav.syfo.varsel

import kotlinx.coroutines.coroutineScope
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.db.*
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.metrics.tellMerVeiledningPlanlagt
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.Oppfolgingstilfelle39Uker
import no.nav.syfo.service.VarselSendtService
import no.nav.syfo.util.VarselUtil
import no.nav.syfo.utils.isEqualOrAfter
import no.nav.syfo.utils.isEqualOrBefore
import no.nav.syfo.utils.todayIsBetweenFomAndTom
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MerVeiledningVarselPlanner(
    val databaseAccess: DatabaseInterface,
    val syfosyketilfelleConsumer: SyfosyketilfelleConsumer,
    val varselSendtService: VarselSendtService
) : VarselPlanner {
    private val nrOfWeeksThreshold = 39L * 7L
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.Varsel39Uker")
    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)
    override val name: String = "MER_VEILEDNING_VARSEL"

    override suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String) = coroutineScope {
        val oppfolgingstilfelle = syfosyketilfelleConsumer.getOppfolgingstilfelle39Uker(aktorId)

        if(oppfolgingstilfelle == null) {
            log.info("[$name]: Fant ikke oppfolgingstilfelle for denne brukeren. Planlegger ikke nytt varsel")
            return@coroutineScope
        }

        val tilfelleFom = oppfolgingstilfelle.fom
        val tilfelleTom = oppfolgingstilfelle.tom

        if (todayIsBetweenFomAndTom(tilfelleFom, tilfelleTom)) {
            varselDate39Uker(oppfolgingstilfelle)?.let { utsendingsdato ->
                val arbeidstakerAktorId = oppfolgingstilfelle.aktorId

                val varsel = PlanlagtVarsel(
                    fnr,
                    arbeidstakerAktorId,
                    emptySet(),
                    VarselType.MER_VEILEDNING,
                    utsendingsdato
                )

                if (varselSendtService.erVarselSendt(fnr, VarselType.MER_VEILEDNING, tilfelleFom, tilfelleTom)) {
                    log.info("[$name]: Varsel har allerede blitt sendt ut til bruker i dette sykeforløpet. Planlegger ikke nytt varsel")
                    return@coroutineScope
                }
                val tidligerePlanlagteVarslerPaFnr = varselUtil.getPlanlagteVarslerAvType(fnr, VarselType.MER_VEILEDNING)

                if (tidligerePlanlagteVarslerPaFnr.isNotEmpty()) {
                    val sisteUsendteVarsel = tidligerePlanlagteVarslerPaFnr.first()
                    databaseAccess.updateUtsendingsdatoByVarselId(sisteUsendteVarsel.uuid, utsendingsdato)
                    log.info("[$name]: Oppdaterte tidligere usendt 39-ukers varsel i samme sykeforlop")
                } else {
                    log.info("[$name]: Planlegger 39-ukers varsel")
                    databaseAccess.storePlanlagtVarsel(varsel)
                    tellMerVeiledningPlanlagt()
                }
            } ?: log.info("[$name]: Antall dager utbetalt er færre enn 39 uker tilsammen i sykefraværet. Planlegger ikke varsel")
        } else {
            log.info("[$name]: Dagens dato er utenfor [fom,tom] intervall til oppfølgingstilfelle. Planlegger ikke varsel")
        }
    }

    private fun varselDate39Uker(tilfelle: Oppfolgingstilfelle39Uker): LocalDate? {
        val fom = tilfelle.fom
        val tom =  tilfelle.tom

        val dagerITilfelleTotalt = ChronoUnit.DAYS.between(fom, tom)
        val dagerSykmeldt = tilfelle.antallSykefravaersDagerTotalt
        val varselDatoTomOffset = (dagerITilfelleTotalt - (dagerSykmeldt - 273))
        val varselDato = tom.minusDays(varselDatoTomOffset)

        val fomPlus39Weeks = fom.plusWeeks(nrOfWeeksThreshold)
        val today = LocalDate.now()
        return if (tom.isEqualOrAfter(fomPlus39Weeks) && today.isEqualOrBefore(fomPlus39Weeks))
            varselDato
        else
            null
    }
}
