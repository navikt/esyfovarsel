package no.nav.syfo.service

import no.nav.syfo.UrlEnv
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SendVarselService(
    val accessControlService: AccessControlService,
    val urlEnv: UrlEnv,
    val merVeiledningVarselService: MerVeiledningVarselService,
    val merVeiledningVarselFinder: MerVeiledningVarselFinder,
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.SendVarselService")

    suspend fun sendVarsel(pPlanlagtVarsel: PPlanlagtVarsel): String {
        return try {
            val userAccessStatus = accessControlService.getUserAccessStatus(pPlanlagtVarsel.fnr)
            val fnr = userAccessStatus.fnr!!

            if (merVeiledningVarselFinder.isBrukerYngreEnn67Ar(fnr)) {
                merVeiledningVarselService.sendVarselTilArbeidstaker(
                    ArbeidstakerHendelse(
                        HendelseType.SM_MER_VEILEDNING,
                        false,
                        null,
                        pPlanlagtVarsel.fnr,
                        null,
                    ),
                    pPlanlagtVarsel.uuid,
                    userAccessStatus,
                )
            }
            pPlanlagtVarsel.type

        } catch (e: RuntimeException) {
            log.error("Feil i utsending av varsel med UUID: ${pPlanlagtVarsel.uuid} | ${e.message}", e)
            UTSENDING_FEILET
        }
    }


}
