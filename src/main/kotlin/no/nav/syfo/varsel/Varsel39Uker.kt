package no.nav.syfo.varsel

import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.consumer.domain.OppfolgingstilfellePerson
import no.nav.syfo.consumer.domain.Sykeforlop
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.utils.isEqualOrAfter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// TODO: Må sjekke at det planlagte varselet er det "minste" tidsmessig før planlegging (syfoservice: Hvis det finnes et utsendt varsel som er større eller lik "inneværende sykeforløp".fom - 13 uker, så skal ikke varsel planlegges)
class Varsel39Uker(val databaseAccess: DatabaseInterface, val syfosyketilfelleConsumer: SyfosyketilfelleConsumer) : VarselPlanner {
    private val antallDageri39Uker = 39L * 7L
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.Varsel39Uker")

    override fun processOppfolgingstilfelle(oppfolgingstilfelle: OppfolgingstilfellePerson, fnr: String) {

        val sykeforlopListe = runBlocking { syfosyketilfelleConsumer.getSykeforlop(oppfolgingstilfelle.aktorId) }
        val antallDagerSykmeldt = beregnDagerSykmeldt(sykeforlopListe)
        val antallDagerIgjenTilVarsling = maxOf(antallDageri39Uker - antallDagerSykmeldt, 0)
        val varselDato = LocalDate.now().plusDays(antallDagerIgjenTilVarsling)
        val sluttDatoForSisteSykmelding = sykeforlopListe.last().sykmeldinger.last().tom

        //TODO: Fjerne logging
        log.info("[39UKER_VARSEL]: sykeforlopListe:  $sykeforlopListe")
        log.info("[39UKER_VARSEL]: antallDagerSykmeldt:  $antallDagerSykmeldt")
        log.info("[39UKER_VARSEL]: varselDato:  $varselDato")
        log.info("[39UKER_VARSEL]: sluttDatoForSisteSykmelding:  $sluttDatoForSisteSykmelding")

        if (sluttDatoForSisteSykmelding.isEqualOrAfter(varselDato)) {
            val varsel = PlanlagtVarsel(
                fnr,
                oppfolgingstilfelle.aktorId,
                emptyList(),                    //Store nothing for now
                VarselType.MER_VEILEDNING,
                varselDato
            )
            log.info("Planlegger 39-ukers varsel")
            databaseAccess.storePlanlagtVarsel(varsel)
        }
    }

    private fun beregnDagerSykmeldt(sykeforlop: List<Sykeforlop>): Long {
        val sykeForlopSortertMedNyesteForst = sykeforlop.sortedByDescending { it.oppfolgingsdato }
        var totaltAntallDagerSykmeldt = 0L
        var startDatoForForrigeSykeforlop = sykeForlopSortertMedNyesteForst.first().sykmeldinger.last().fom

        sykeForlopSortertMedNyesteForst.forEach {
            // Hvis det har gått mer enn 26 uker mellom sykeforløp, så ignorerer vi de resterende sykeforløpene siden arbeidstaker har opparbeidet ny rett til sykepenger
            val sluttDatoForSykeforlop = it.sykmeldinger.last().tom
            if (startDatoForForrigeSykeforlop.minusWeeks(26).isAfter(sluttDatoForSykeforlop)) {
                return@forEach
            }

            totaltAntallDagerSykmeldt += beregnDagerIEtSykeforlop(it)
            startDatoForForrigeSykeforlop = it.oppfolgingsdato
        }

        return totaltAntallDagerSykmeldt
    }

    private fun beregnDagerIEtSykeforlop(sykeforlop: Sykeforlop): Long {
        var antallDagerISykeforlopet = 0L

        sykeforlop.sykmeldinger.forEach {
            // Vi regner bare med dager som har vært i fortiden
            val sykmeldtTil = if (it.tom.isBefore(LocalDate.now())) it.tom else LocalDate.now()
            val dagerISykmeldingen = it.fom.until(sykmeldtTil, ChronoUnit.DAYS)
            antallDagerISykeforlopet += dagerISykmeldingen
        }
        return antallDagerISykeforlopet
    }
}
