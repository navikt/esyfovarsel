package no.nav.syfo.utils

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.fetchSykmeldingerIdByPlanlagtVarselsUUID
import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain.Oppfolgingstilfelle39Uker
import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain.Syketilfelledag
import no.nav.syfo.syketilfelle.domain.Tag
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

val antallUker39UkersVarsel = 39L
val antallDager39UkersVarsel = antallUker39UkersVarsel * 7L + 1
val SVAR_MOTEBEHOV_DAGER: Long = 112
val MAX_ANTALL_REMAINING_SYKEDAGER_39_UKERS_VARSEL = 65
val MAX_ANTALL_UKER_TIL_MAKSDATO_39_UKERS_VARSEL = 13
val MAX_ANTALL_DAGER_TIL_MAKSDATO_39_UKERS_VARSEL = MAX_ANTALL_UKER_TIL_MAKSDATO_39_UKERS_VARSEL * 7L + 1

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

        val isFravarForSykmelding = syketilfelledag.prioritertSyketilfellebit?.tags?.contains(Tag.FRAVAR_FOR_SYKMELDING) == true

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
