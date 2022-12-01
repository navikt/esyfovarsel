package no.nav.syfo.service

import java.time.*
import no.nav.syfo.consumer.pdl.*
import no.nav.syfo.db.*
import no.nav.syfo.db.domain.*
import no.nav.syfo.syketilfelle.*
import no.nav.syfo.utils.*

class VarselSendtService(
    val pdlConsumer: PdlConsumer,
    val syketilfellebitService: SyketilfellebitService,
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
