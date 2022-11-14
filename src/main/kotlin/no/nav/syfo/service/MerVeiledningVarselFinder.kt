package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchMerVeiledningVarslerToSend
import no.nav.syfo.db.fetchUtsendteMerVeiledningVarslerSiste3Maneder
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

class MerVeiledningVarselFinder(
    private val databaseAccess: DatabaseInterface,
    private val sykmeldingService: SykmeldingService
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.service.VarselSenderService")

    suspend fun findMerVeiledningVarslerToSendToday(): List<PPlanlagtVarsel> {
        val alleMerVeiledningVarslerToSendBasertPaUtbetaling = databaseAccess.fetchMerVeiledningVarslerToSend() //UTB
        val utsendteMerVeiledningVarslerSiste3Maneder = databaseAccess.fetchUtsendteMerVeiledningVarslerSiste3Maneder()
        val merVeiledningVarslerBasertPaUtbetalingUtenVarslete = alleMerVeiledningVarslerToSendBasertPaUtbetaling.filter { v -> utsendteMerVeiledningVarslerSiste3Maneder.none { v.fnr == it.fnr } }
        val merVeiledningVarslerSomErSykmeldtIDag = merVeiledningVarslerBasertPaUtbetalingUtenVarslete.filter { sykmeldingService.isPersonSykmeldtPaDato(LocalDate.now(), it.fnr) }

        log.info("Antall MER_VEILEDNING varsler fra Spleis/Infotrygd: ${alleMerVeiledningVarslerToSendBasertPaUtbetaling.size}")
        log.info("Antall MER_VEILEDNING varsler fra Spleis/Infotrygd som ikke har fått varsel siste 3mnd: ${merVeiledningVarslerBasertPaUtbetalingUtenVarslete.size}")
        log.info("Antall MER_VEILEDNING varsler fra Spleis/Infotrygd som ikke har fått varsel siste 3mnd og som er sykmeldt: ${merVeiledningVarslerSomErSykmeldtIDag.size}")

        return merVeiledningVarslerSomErSykmeldtIDag.map {
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
