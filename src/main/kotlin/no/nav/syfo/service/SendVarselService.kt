package no.nav.syfo.service

import no.nav.syfo.UrlEnv
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.kafka.brukernotifikasjoner.BeskjedKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

class SendVarselService(
    val beskjedKafkaProducer: BeskjedKafkaProducer,
    val accessControl: AccessControl,
    val urlEnv: UrlEnv
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.db.SendVarselService")

    fun sendVarsel(pPlanlagtVarsel: PPlanlagtVarsel): String {
        // Recheck if user can be notified in case of recent 'Addressesperre'
        return try {
            val fodselnummer = accessControl.getFnrIfUserCanBeNotified(pPlanlagtVarsel.aktorId)
            val uuid = pPlanlagtVarsel.uuid
            log.info("fodselnummer: $fodselnummer") // TODO Delete
            fodselnummer?.let { fnr ->
                val varselUrl = varselUrlFromType(pPlanlagtVarsel.type)
                val varselContent = varselContentFromType(pPlanlagtVarsel.type)

                if (varselUrl !== null && varselContent !== null) {
                    beskjedKafkaProducer.sendBeskjed(fnr, varselContent, uuid, varselUrl)
                    return pPlanlagtVarsel.type
                } else {
                    throw RuntimeException("Klarte ikke mappe typestreng til innholdstekst og URL")
                }
            } ?: UTSENDING_FEILET
        } catch (e: RuntimeException) {
            log.error("Feil i utsending av varsel med UUID: ${pPlanlagtVarsel.uuid} | ${e.message}", e)
            UTSENDING_FEILET
        }
    }

    private fun varselContentFromType(type: String): String? {
        return when (type) {
            VarselType.AKTIVITETSKRAV.toString() -> "NAV skal nå vurdere aktivitetsplikten din"
            VarselType.MER_VEILEDNING.toString() -> "Det nærmer seg datoen da du ikke lenger kan få sykepenger."
            else -> null
        }
    }

    private fun varselUrlFromType(type: String): URL? {
        val baseUrlSykInfo = urlEnv.baseUrlSykInfo
        val aktivitetskravUrl = URL(baseUrlSykInfo + "/aktivitetsplikt")
        val merVeiledningUrl = URL(baseUrlSykInfo + "/snart-slutt-pa-sykepengene")

        return when (type) {
            VarselType.AKTIVITETSKRAV.toString() -> aktivitetskravUrl
            VarselType.MER_VEILEDNING.toString() -> merVeiledningUrl
            else -> null
        }
    }
}
