package no.nav.syfo.varsel

import kotlinx.coroutines.coroutineScope
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.utils.isEqualOrAfter
import no.nav.syfo.utils.todayIsBetweenFomAndTom
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

// TODO: Må sjekke at det planlagte varselet er det "minste" tidsmessig før planlegging (syfoservice: Hvis det finnes et utsendt varsel som er større eller lik "inneværende sykeforløp".fom - 13 uker, så skal ikke varsel planlegges)
class MerVeiledningVarselPlanner(val databaseAccess: DatabaseInterface, val syfosyketilfelleConsumer: SyfosyketilfelleConsumer) : VarselPlanner {
    private val nrOfWeeksThreshold = 39L
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.Varsel39Uker")

    override suspend fun processOppfolgingstilfelle(aktorId: String, fnr: String) = coroutineScope {
        val oppfolgingstilfelle = syfosyketilfelleConsumer.getOppfolgingstilfelle39Uker(aktorId)
            ?: throw RuntimeException("[39-UKER_VARSEL]: Oppfolgingstilfelle is null")

        val tilfelleFom = oppfolgingstilfelle.fom
        val tilfelleTom = oppfolgingstilfelle.tom

        if (todayIsBetweenFomAndTom(tilfelleFom, tilfelleTom)) {
            varselDate39Uker(tilfelleFom, tilfelleTom)?.let {
                val arbeidstakerAktorId = oppfolgingstilfelle.aktorId

                val varsel = PlanlagtVarsel(
                    fnr,
                    arbeidstakerAktorId,
                    emptySet(),
                    VarselType.MER_VEILEDNING,
                    it
                )

                log.info("[39-UKER_VARSEL]: Planlegger 39-ukers varsel")
                databaseAccess.storePlanlagtVarsel(varsel)
            } ?: log.info("[39-UKER_VARSEL]: Antall dager betalt av NAV er færre enn 39 uker tilsammen i sykefraværet. Planlegger ikke varsel")
        } else {
            log.info("[39-UKER_VARSEL]: Dagens dato er utenfor [fom,tom] intervall til oppfølgingstilfelle. Planlegger ikke varsel")
        }
    }

    private fun varselDate39Uker(fom: LocalDate, tom: LocalDate): LocalDate? {
        val fomPlus39Weeks = fom.plusWeeks(nrOfWeeksThreshold)
        return if (tom.isEqualOrAfter(fomPlus39Weeks))
            fomPlus39Weeks
            else
                null
    }
}
