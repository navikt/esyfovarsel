package no.nav.syfo.job

import no.nav.syfo.ToggleEnv
import no.nav.syfo.db.*
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.metrics.tellAktivitetskravVarselSendt
import no.nav.syfo.metrics.tellMerVeiledningVarselSendt
import no.nav.syfo.metrics.tellSvarMotebehovVarselSendt
import no.nav.syfo.service.SendVarselService
import org.slf4j.LoggerFactory
import java.time.LocalDate

class VarselSender(
    private val databaseAccess: DatabaseInterface,
    private val sendVarselService: SendVarselService,
    private val toggles: ToggleEnv,
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.job.SendVarslerJobb")

    suspend fun sendVarsler(): Int {
        log.info("Starter SendVarslerJobb")

        val varslerSendt = HashMap<String, Int>()
        var varslerToSendToday = databaseAccess.fetchPlanlagtVarselByUtsendingsdato(LocalDate.now())

        if (toggles.sendMerVeiledningVarslerBasedOnMaxDate) {
            val varslerToSendTodayMonthMerVeiledning = databaseAccess.fetchPlanlagtMerVeiledningVarselByUtsendingsdato(LocalDate.now())
            val allVarslerToSendTodayMerVeiledning = varslerToSendTodayMonthMerVeiledning.plus(getAllUnsentMerVeiledningVarslerLastMonth())

            varslerToSendToday = mergePlanlagteVarsler(varslerToSendToday, allVarslerToSendTodayMerVeiledning)
            log.info("Planlegger å sende ${allVarslerToSendTodayMerVeiledning.size} Mer veiledning varsler med utsending basert på maxdato")
        }

        log.info("Planlegger å sende ${varslerToSendToday.size} varsler totalt")

        if (!toggles.sendAktivitetskravVarsler) log.info("Utsending av Aktivitetskrav er ikke aktivert, og varsler av denne typen blir ikke sendt")
        if (!toggles.sendMerVeiledningVarsler) log.info("Utsending av Mer veiledning er ikke aktivert, og varsler av denne typen blir ikke sendt")
        if (!toggles.sendMerVeiledningVarslerBasedOnMaxDate) log.info("Utsending av  Mer veiledning med utsending basert på maxdato er ikke aktivert, og varsler av denne typen blir ikke sendt via denne pathen")

        varslerToSendToday.forEach {
            if (skalSendeVarsel(it)) {
                log.info("Sender varsel med UUID ${it.uuid}")
                val type = sendVarselService.sendVarsel(it)
                if (type.sendtUtenFeil()) {
                    incrementVarselCountMap(varslerSendt, type)
                    log.info("Markerer varsel med UUID ${it.uuid} som sendt")
                    databaseAccess.storeUtsendtVarsel(it)
                    databaseAccess.deletePlanlagtVarselByVarselId(it.uuid)
                }
            }
        }

        varslerSendt.forEach { (key, value) ->
            log.info("Sendte $value varsler av type $key")
        }

        val antallMerVeiledningSendt = varslerSendt[VarselType.MER_VEILEDNING.name] ?: 0
        val antallAktivitetskravSendt = varslerSendt[VarselType.AKTIVITETSKRAV.name] ?: 0
        val antallSvarMotebehovSendt = varslerSendt[VarselType.SVAR_MOTEBEHOV.name] ?: 0

        tellMerVeiledningVarselSendt(antallMerVeiledningSendt)
        tellAktivitetskravVarselSendt(antallAktivitetskravSendt)
        tellSvarMotebehovVarselSendt(antallSvarMotebehovSendt)

        log.info("Avslutter SendVarslerJobb")

        return antallMerVeiledningSendt + antallAktivitetskravSendt
    }

    private fun incrementVarselCountMap(map: HashMap<String, Int>, type: String) {
        val count = (map[type] ?: 0) + 1
        map[type] = count
    }

    private fun skalSendeVarsel(it: PPlanlagtVarsel) =
        (it.type == VarselType.MER_VEILEDNING.name && toggles.sendMerVeiledningVarsler || toggles.sendMerVeiledningVarslerBasedOnMaxDate) ||
                (it.type == VarselType.AKTIVITETSKRAV.name && toggles.sendAktivitetskravVarsler)

    private fun String.sendtUtenFeil(): Boolean {
        return this != UTSENDING_FEILET
    }

    fun mergePlanlagteVarsler(
        plannedVarslerFromDatabase: List<PPlanlagtVarsel>,
        plannedMerVeiledningVarslerBasedOnMaxDate: List<PPlanlagtVarsel>,
    ): List<PPlanlagtVarsel> {
        var mergetVarslerList = listOf<PPlanlagtVarsel>()
        plannedMerVeiledningVarslerBasedOnMaxDate.forEach {
            val currentFnr = it.fnr
            deletePlannedMerVeiledningVarselDuplicateByFnr(currentFnr, plannedVarslerFromDatabase)
            mergetVarslerList = mergetVarslerList.plus(it)
        }
        return mergetVarslerList
    }

    fun deletePlannedMerVeiledningVarselDuplicateByFnr(
        fnr: String,
        plannedVarslerFromDatabase: List<PPlanlagtVarsel>,
    ) {
        plannedVarslerFromDatabase as MutableList<PPlanlagtVarsel>
        val iterator = plannedVarslerFromDatabase.iterator()

        while (iterator.hasNext()) {
            val i = iterator.next()
            if (i.fnr == fnr && i.type == VarselType.MER_VEILEDNING.name) {
                iterator.remove()
                databaseAccess.deletePlanlagtVarselByVarselId(i.uuid)
            }
        }
    }

    fun getAllUnsentMerVeiledningVarslerLastMonth(): List<PPlanlagtVarsel> {
        val unsentMerVeiledningVarslerLastMonth = databaseAccess.fetchPlanlagtMerVeiledningVarselBySendingDateSisteManed() // in max date table!
        val sentMerVeiledningVarslerLastMonth = databaseAccess.fetchUtsendteVarslerSisteManed().filter { it.type == VarselType.MER_VEILEDNING.name }

        return unsentMerVeiledningVarslerLastMonth.filter { plannedVarsel -> sentMerVeiledningVarslerLastMonth.none { plannedVarsel.fnr == it.fnr } }
    }
}
