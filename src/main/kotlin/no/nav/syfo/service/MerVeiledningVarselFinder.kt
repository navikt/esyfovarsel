package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchMerVeiledningVarslerToSend
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

class MerVeiledningVarselFinder(
    private val databaseAccess: DatabaseInterface,
    private val sykmeldingService: SykmeldingService
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.service.MerVeiledningVarselFinder")

    suspend fun findMerVeiledningVarslerToSendToday(): List<PPlanlagtVarsel> {
        log.info("[MerVeiledningVarselFinder] Henter kandidater for Mer veiledning-varsler")
        val alleMerVeiledningVarsler = databaseAccess.fetchMerVeiledningVarslerToSend() //UTB

        log.info("[MerVeiledningVarselFinder] Slår opp sykmeldinger")
        val merVeiledningVarslerSomSkalSendesIDag = alleMerVeiledningVarsler
            .filter { sykmeldingService.isPersonSykmeldtPaDato(LocalDate.now(), it.fnr) }

        log.info("Antall MER_VEILEDNING varsler fra Spleis/Infotrygd: ${merVeiledningVarslerSomSkalSendesIDag.size}")

        return merVeiledningVarslerSomSkalSendesIDag.map {
            PPlanlagtVarsel(
                uuid = it.id.toString(),
                fnr = it.fnr,
                orgnummer = null,
                aktorId = null,
                type = VarselType.MER_VEILEDNING.name,
                utsendingsdato = LocalDate.now(),
                sistEndret = LocalDateTime.now(),
                opprettet = LocalDateTime.now(),
            )
        }
    }
}
