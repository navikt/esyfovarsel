package no.nav.syfo.varsel

import kotlinx.coroutines.coroutineScope
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.db.*
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.util.VarselUtil
import no.nav.syfo.utils.isEqualOrAfter
import no.nav.syfo.utils.isEqualOrBefore
import no.nav.syfo.utils.todayIsBetweenFomAndTom
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

class MerVeiledningVarselPlanner(val databaseAccess: DatabaseInterface, val syfosyketilfelleConsumer: SyfosyketilfelleConsumer) : VarselPlanner {
    private val nrOfWeeksThreshold = 39L
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.Varsel39Uker")
    private val varselUtil: VarselUtil = VarselUtil(databaseAccess)
    override val name: String = "MER_VEILEDNING_VARSEL"

    override suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String) = coroutineScope {
        val oppfolgingstilfelle = syfosyketilfelleConsumer.getOppfolgingstilfelle39Uker(aktorId)
            ?: throw RuntimeException("[$name]: Oppfolgingstilfelle er null")

        val tilfelleFom = oppfolgingstilfelle.fom
        val tilfelleTom = oppfolgingstilfelle.tom

        if (todayIsBetweenFomAndTom(tilfelleFom, tilfelleTom)) {
            varselDate39Uker(tilfelleFom, tilfelleTom)?.let { utsendingsdato ->
                val arbeidstakerAktorId = oppfolgingstilfelle.aktorId

                val varsel = PlanlagtVarsel(
                    fnr,
                    arbeidstakerAktorId,
                    emptySet(),
                    VarselType.MER_VEILEDNING,
                    utsendingsdato
                )

                val tidligereVarsler39UkersVarslerPaFnr = varselUtil.getPlanlagteVarslerAvType(fnr, VarselType.MER_VEILEDNING)
                if (tidligereVarsler39UkersVarslerPaFnr.isNotEmpty()) {
                    val sisteUsendteVarsel = tidligereVarsler39UkersVarslerPaFnr
                        .sortedBy { tidligereVarsel -> tidligereVarsel.utsendingsdato }
                        .lastOrNull { it.ikkeUtsendtEnna() }

                    sisteUsendteVarsel?.let {
                        log.info("[$name]: Oppdaterer tidligere usendt 39-ukers varsel i samme sykeforlop")
                        databaseAccess.updateUtsendingsdatoByVarselId(sisteUsendteVarsel.uuid, utsendingsdato)
                    } ?: log.info("[$name]: Varsel har allerede blitt sendt ut til bruker i dette sykeforløpet. Planlegger ikke varsel")
                } else {
                    log.info("[$name]: Planlegger 39-ukers varsel")
                    databaseAccess.storePlanlagtVarsel(varsel)
                }
            } ?: log.info("[$name]: Antall dager utbetalt er færre enn 39 uker tilsammen i sykefraværet. Planlegger ikke varsel")
        } else {
            log.info("[$name]: Dagens dato er utenfor [fom,tom] intervall til oppfølgingstilfelle. Planlegger ikke varsel")
        }
    }

    private fun varselDate39Uker(fom: LocalDate, tom: LocalDate): LocalDate? {
        val fomPlus39Weeks = fom.plusWeeks(nrOfWeeksThreshold)
        val today = LocalDate.now()
        return if (tom.isEqualOrAfter(fomPlus39Weeks) && today.isEqualOrBefore(fomPlus39Weeks))
            fomPlus39Weeks
        else
            null
    }
}
