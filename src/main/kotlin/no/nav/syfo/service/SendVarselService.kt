package no.nav.syfo.service

import no.nav.syfo.UrlEnv
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

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

            val varselUrl = varselUrlFromType(pPlanlagtVarsel.type)
            val varselContent = varselContentFromType(pPlanlagtVarsel.type)

            if (varselUrl !== null && varselContent !== null) {
                if (userSkalVarsles(pPlanlagtVarsel.type)) {
                    when (pPlanlagtVarsel.type) {
                        MER_VEILEDNING.name -> {
                            if (merVeiledningVarselFinder.isBrukerYngreEnn67Ar(fnr)) {
                                sendMerVeiledningVarselTilArbeidstaker(pPlanlagtVarsel, userAccessStatus)
                            }
                            pPlanlagtVarsel.type
                        }

                        else -> {
                            throw RuntimeException("Ukjent typestreng")
                        }
                    }
                } else {
                    log.info("Bruker med forespurt fnr er reservert eller gradert og kan ikke varsles")
                    UTSENDING_FEILET
                }
            } else {
                throw RuntimeException("Klarte ikke mappe typestreng til innholdstekst og URL")
            }
        } catch (e: RuntimeException) {
            log.error("Feil i utsending av varsel med UUID: ${pPlanlagtVarsel.uuid} | ${e.message}", e)
            UTSENDING_FEILET
        }
    }

    private fun userSkalVarsles(varselType: String): Boolean {
        return varselType == MER_VEILEDNING.name
    }

    private suspend fun sendMerVeiledningVarselTilArbeidstaker(
        pPlanlagtVarsel: PPlanlagtVarsel,
        userAccessStatus: UserAccessStatus,
    ) {
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

    private fun varselContentFromType(type: String): String? {
        return when (type) {
            MER_VEILEDNING.name -> "Det nærmer seg datoen da du ikke lenger kan få sykepenger."
            else -> null
        }
    }

    private fun varselUrlFromType(type: String): URL? {
        val baseUrlSykInfo = urlEnv.baseUrlSykInfo
        val merVeiledningUrl = URL(baseUrlSykInfo + "/snart-slutt-pa-sykepengene")

        return when (type) {
            MER_VEILEDNING.name -> merVeiledningUrl
            else -> null
        }
    }
}
