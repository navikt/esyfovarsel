package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchMerVeiledningVarslerToSend
import no.nav.syfo.db.fetchPlanlagtVarselByUtsendingsdato
import no.nav.syfo.db.fetchUtsendteMerVeiledningVarslerSiste3Maneder
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

class VarselSenderService(
    private val databaseAccess: DatabaseInterface,
    private val sykmeldingService: SykmeldingService
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.service.VarselSenderService")

    suspend fun getVarslerToSendToday(): List<PPlanlagtVarsel> {
        val gamlePlanlagteVarslerToSendToday = databaseAccess.fetchPlanlagtVarselByUtsendingsdato(LocalDate.now())
        val planlagteMerVeiledningVarsler = gamlePlanlagteVarslerToSendToday.filter { it.type == VarselType.MER_VEILEDNING.name }
        val planlagteVarslerOtherTypes = gamlePlanlagteVarslerToSendToday.filter { it.type != VarselType.MER_VEILEDNING.name }

        val alleMerVeiledningVarslerToSendBasedOnUtbetaling = databaseAccess.fetchMerVeiledningVarslerToSend() //UTB
        val utsendteMerVeiledningVarslerSiste3Maneder = databaseAccess.fetchUtsendteMerVeiledningVarslerSiste3Maneder()
        val merVeiledningBasertPaUtbetalingToSend = alleMerVeiledningVarslerToSendBasedOnUtbetaling.filter { v -> utsendteMerVeiledningVarslerSiste3Maneder.none { v.fnr == it.fnr } }
        val planlagteMerVeiledningVarslerUtenDuplikater = planlagteMerVeiledningVarsler.filter { pv -> merVeiledningBasertPaUtbetalingToSend.none { pv.fnr == it.fnr } }

        val convertedMerVeiledningBasertPaUtbetaling = mutableListOf<PPlanlagtVarsel>()

        merVeiledningBasertPaUtbetalingToSend.forEach {
            convertedMerVeiledningBasertPaUtbetaling.add(
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
            )
        }

        val convertedMerVeiledningBasertPaUtbetalingActive = convertedMerVeiledningBasertPaUtbetaling.filter { sykmeldingService.isPersonSykmeldtPaDato(it.utsendingsdato, it.fnr) }
        val mergedMerVeiledningVarsler = planlagteMerVeiledningVarslerUtenDuplikater.plus(convertedMerVeiledningBasertPaUtbetalingActive)

        val varslerToSendToday = planlagteVarslerOtherTypes.plus(mergedMerVeiledningVarsler)

        log.info("Antall MER_VEILEDNING varsler fra Spleis/Infotrygd: ${alleMerVeiledningVarslerToSendBasedOnUtbetaling.size}")
        log.info("Antall MER_VEILEDNING varsler fra Spleis/Infotrygd minus utsendte siste 3mnd: ${merVeiledningBasertPaUtbetalingToSend.size}")
        log.info("Antall MER_VEILEDNING varsler fra Spleis/Infotrygd minus utsendte siste 3mnd minus uten aktiv sykmelding: ${convertedMerVeiledningBasertPaUtbetalingActive.size}")
        log.info("Antall MER_VEILEDNING varsler (planlagte MER_VEILEDNING OG fra Spleis/Infotrygd) minus utsendte siste 3mnd: ${mergedMerVeiledningVarsler.size}")
        log.info(
            "Antall varsler av alle typer (planlagte OG fra Spleis/Infotrygd) minus utsendte siste 3mnd: ${
                varslerToSendToday.size
            }"
        )
        return varslerToSendToday
    }
}