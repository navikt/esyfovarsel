package no.nav.syfo.service

import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchUtsendtVarselByFnr
import no.nav.syfo.utils.dateIsInInterval
import no.nav.syfo.utils.isEqualOrBefore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.RuntimeException
import java.time.LocalDate
import javax.ws.rs.ForbiddenException

class VarselSendtService(
    val pdlConsumer: PdlConsumer,
    val syfosyketilfelleConsumer: SyfosyketilfelleConsumer,
    val databaseAccess: DatabaseInterface
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.db.VarselSendtService")

    fun varselErSendtOgGyldig(innloggetFnr: String, aktorId: String, varselType: VarselType): Boolean {
        val fnr = pdlConsumer.getFnr(aktorId) ?: throw RuntimeException("Klarte ikke hente aktorId ved sjekk på 39-ukers oppgave")
        if (fnr != innloggetFnr) {
            log.warn("Innlogget bruker spør på annen aktørId enn sin egen")
            throw ForbiddenException("Uautorisert forespørsel")
        }
        val syketilfelle = runBlocking {
            syfosyketilfelleConsumer.getOppfolgingstilfelle39Uker(aktorId)
        }
        return syketilfelle?.let {
            val idag = LocalDate.now()
            val fom = syketilfelle.fom
            val tom = syketilfelle.tom
            return idag.isEqualOrBefore(tom) && erVarselSendt(fnr, varselType, fom, tom)
        } ?: false
    }

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
