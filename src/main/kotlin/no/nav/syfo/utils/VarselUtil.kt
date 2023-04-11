package no.nav.syfo.utils

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.fetchSykmeldingerIdByPlanlagtVarselsUUID
import no.nav.syfo.kafka.consumers.syketilfelle.domain.Oppfolgingstilfelle39Uker
import no.nav.syfo.kafka.consumers.syketilfelle.domain.Syketilfelledag
import no.nav.syfo.syketilfelle.domain.Tag

val antallUker39UkersVarsel = 39L
val antallDager39UkersVarsel = antallUker39UkersVarsel * 7L + 1
val REMAINING_WEEKS_UNTIL_39_UKERS_VARSEL = 13
val REMAINING_DAYS_UNTIL_39_UKERS_VARSEL = REMAINING_WEEKS_UNTIL_39_UKERS_VARSEL * 7L + 1

class VarselUtil(private val databaseAccess: DatabaseInterface) {
    fun isVarselDatoForIDag(varselDato: LocalDate): Boolean {
        return varselDato.isBefore(LocalDate.now())
    }

    fun varselDate39Uker(tilfelle: Oppfolgingstilfelle39Uker): LocalDate? {
        val varselDatoTomOffset = (tilfelle.antallSykefravaersDagerTotalt - antallDager39UkersVarsel)
        val varselDato = tilfelle.tom.minusDays(varselDatoTomOffset)

        val isAntallSykedagerPast39Uker = tilfelle.antallSykefravaersDagerTotalt >= antallDager39UkersVarsel
        val isTomAfterVarselDato = tilfelle.tom.isEqualOrAfter(varselDato)
        val isVarselDatoExpired = varselDato.isBefore(LocalDate.now())

        return if (isAntallSykedagerPast39Uker && isTomAfterVarselDato && !isVarselDatoExpired) varselDato else null
    }

    fun isVarselDatoEtterTilfelleSlutt(varselDato: LocalDate, tilfelleSluttDato: LocalDate): Boolean {
        return tilfelleSluttDato.isEqual(varselDato) || varselDato.isAfter(tilfelleSluttDato)
    }

    fun getPlanlagteVarslerAvType(fnr: String, varselType: VarselType): List<PPlanlagtVarsel> {
        return databaseAccess.fetchPlanlagtVarselByFnr(fnr)
            .filter { it.type == varselType.name }
    }

    fun hasLagreteVarslerForForespurteSykmeldinger(planlagteVarsler: List<PPlanlagtVarsel>, ressursIds: Set<String>): Boolean {
        val gjeldendeSykmeldinger = mutableSetOf<Set<String>>()
        for (v: PPlanlagtVarsel in planlagteVarsler) {
            val sm = databaseAccess.fetchSykmeldingerIdByPlanlagtVarselsUUID(v.uuid)
            gjeldendeSykmeldinger.add(sm.toSet())
        }
        val gjeldendeSykmeldingerSet = gjeldendeSykmeldinger.flatten().toSet()
        return (ressursIds intersect gjeldendeSykmeldingerSet).isNotEmpty()
    }

    fun isValidSyketilfelledag(syketilfelledag: Syketilfelledag): Boolean {
        val hasValidDocumentType = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(Tag.SYKMELDING) == true ||
                syketilfelledag.prioritertSyketilfellebit?.tags?.contains(Tag.PAPIRSYKMELDING) == true ||
                syketilfelledag.prioritertSyketilfellebit?.tags?.contains(Tag.SYKEPENGESOKNAD) == true

        val isAcceptedDocument = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(Tag.SENDT) == true ||
                syketilfelledag.prioritertSyketilfellebit?.tags?.contains(Tag.BEKREFTET) == true

        val isBehandlingsdag = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(Tag.BEHANDLINGSDAGER) == true

        val isFravarForSykmelding = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(Tag.FRAVAR_FOR_SYKMELDING) == true ||
                syketilfelledag.prioritertSyketilfellebit?.tags?.contains(Tag.EGENMELDING) == true

        return hasValidDocumentType && isAcceptedDocument && !isBehandlingsdag && !isFravarForSykmelding
    }

    fun calculateActualNumberOfDaysInTimeline(validSyketilfelledager: List<Syketilfelledag>): Int {
        val first = validSyketilfelledager[0].prioritertSyketilfellebit
        var actualNumberOfDaysInTimeline = ChronoUnit.DAYS.between(first!!.fom, first.tom).toInt()

        for (i in 1 until validSyketilfelledager.size) {
            val currentFom = validSyketilfelledager[i].prioritertSyketilfellebit!!.fom
            val currentTom = validSyketilfelledager[i].prioritertSyketilfellebit!!.tom
            val previousTom = validSyketilfelledager[i - 1].prioritertSyketilfellebit!!.tom

            if (currentFom.isEqualOrAfter(previousTom)) {
                val currentLength = ChronoUnit.DAYS.between(currentFom, currentTom).toInt()
                actualNumberOfDaysInTimeline += currentLength
            }
        }
        return actualNumberOfDaysInTimeline
    }
}
