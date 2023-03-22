package no.nav.syfo.service

import java.time.LocalDate
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchUtsendtVarselByFnr
import no.nav.syfo.utils.dateIsInInterval

class VarselSendtService(
    val databaseAccess: DatabaseInterface
) {
    fun erVarselSendt(fnr: String, varselType: VarselType, tilfelleFom: LocalDate, tilfelleTom: LocalDate): Boolean {
        val utsendtVarsel = databaseAccess.fetchUtsendtVarselByFnr(fnr)
            .filter { it.type == varselType.name }

        if (utsendtVarsel.isEmpty()) {
            return false
        }

        val sisteUtsendeVarsel = utsendtVarsel
            .sortedBy { it.utsendtTidspunkt }
            .last()
        val sisteGangVarselBleUtsendt = sisteUtsendeVarsel.utsendtTidspunkt.toLocalDate()
        return dateIsInInterval(sisteGangVarselBleUtsendt, tilfelleFom, tilfelleTom)
    }
}
