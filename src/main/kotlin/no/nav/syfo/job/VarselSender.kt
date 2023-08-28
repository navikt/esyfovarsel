package no.nav.syfo.job

import no.nav.syfo.ToggleEnv
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deletePlanlagtVarselByVarselId
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.metrics.tellAktivitetskravVarselSendt
import no.nav.syfo.metrics.tellMerVeiledningVarselSendt
import no.nav.syfo.service.AktivitetskravVarselFinder
import no.nav.syfo.service.MerVeiledningVarselFinder
import no.nav.syfo.service.SendVarselService
import org.slf4j.LoggerFactory

class VarselSender(
    private val databaseAccess: DatabaseInterface,
    private val sendVarselService: SendVarselService,
    private val aktivitetskravVarselFinder: AktivitetskravVarselFinder,
    private val merVeiledningVarselFinder: MerVeiledningVarselFinder,
    private val toggles: ToggleEnv,
) {
    private val log = LoggerFactory.getLogger(VarselSender::class.java)

    suspend fun sendVarsler(): Int {
        log.info("Starter SendVarslerJobb")

        val varslerSendt = HashMap<String, Int>()

        val varslerToSendToday = aktivitetskravVarselFinder.findAktivitetskravVarslerToSendToday()
            .plus(merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday())

        log.info("Planlegger å sende ${varslerToSendToday.size} varsler totalt")

        if (!toggles.sendAktivitetskravVarsler) log.info("Utsending av Aktivitetskrav er ikke aktivert, og varsler av" +
                "denne typen blir ikke sendt")

        varslerToSendToday.forEach {
            if (isVarselToggleAkivert(it) && skalVarsleBrukerPgaAlder(it)) {
                val type = sendVarselService.sendVarsel(it)
                if (type.sendtUtenFeil()) {
                    incrementVarselCountMap(varslerSendt, type)
                    log.info("Sendt varsel med UUID ${it.uuid}")
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

        tellMerVeiledningVarselSendt(antallMerVeiledningSendt)
        tellAktivitetskravVarselSendt(antallAktivitetskravSendt)

        log.info("Avslutter SendVarslerJobb")

        return antallMerVeiledningSendt + antallAktivitetskravSendt
    }

    private fun incrementVarselCountMap(map: HashMap<String, Int>, type: String) {
        val count = (map[type] ?: 0) + 1
        map[type] = count
    }

    private fun isVarselToggleAkivert(it: PPlanlagtVarsel) =
        (it.type == VarselType.MER_VEILEDNING.name) ||
            (it.type == VarselType.AKTIVITETSKRAV.name && toggles.sendAktivitetskravVarsler)

    private fun skalVarsleBrukerPgaAlder(pPlanlagtVarsel: PPlanlagtVarsel) =
        (pPlanlagtVarsel.type == VarselType.AKTIVITETSKRAV.name && aktivitetskravVarselFinder.isBrukerYngreEnn70Ar(pPlanlagtVarsel.fnr)) ||
            (pPlanlagtVarsel.type == VarselType.MER_VEILEDNING.name && merVeiledningVarselFinder.isBrukerYngreEnn67Ar(pPlanlagtVarsel.fnr))

    private fun String.sendtUtenFeil(): Boolean {
        return this != UTSENDING_FEILET
    }
}
