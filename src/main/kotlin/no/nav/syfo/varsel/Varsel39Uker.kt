package no.nav.syfo.varsel

import no.nav.syfo.consumer.domain.OppfolgingstilfellePerson
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.utils.isEqualOrAfter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// TODO: Må sjekke at det planlagte varselet er det "minste" tidsmessig før planlegging (syfoservice: Hvis det finnes et utsendt varsel som er større eller lik "inneværende sykeforløp".fom - 13 uker, så skal ikke varsel planlegges)
class Varsel39Uker(val databaseAccess: DatabaseInterface) : VarselPlanner {
    private val nrOfWeeksThreshold = 39L
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.Varsel39Uker")

    override fun processOppfolgingstilfelle(oppfolgingstilfelle: OppfolgingstilfellePerson, fnr: String) {
        val tilfelleFirstFomPlus39Weeks = oppfolgingstilfelle.tidslinje.first().dag.plusWeeks(nrOfWeeksThreshold)
        val tilfelleLastTom = oppfolgingstilfelle.tidslinje.last().dag

        val arbeidstakerAktorId = oppfolgingstilfelle.aktorId

        if (tilfelleLastTom.isEqualOrAfter(tilfelleFirstFomPlus39Weeks)) {
            val varsel = PlanlagtVarsel(
                fnr,
                arbeidstakerAktorId,
                emptyList(),                    //Store nothing for now
                VarselType.MER_VEILEDNING,
                tilfelleFirstFomPlus39Weeks
            )
            log.info("Planlegger 39-ukers varsel")
            databaseAccess.storePlanlagtVarsel(varsel)
        }
    }
}
