package no.nav.syfo.service

import no.nav.syfo.DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST
import no.nav.syfo.UrlEnv
import no.nav.syfo.consumer.arbeidsgiverNotifikasjonProdusent.ArbeidsgiverNotifikasjonProdusentConsumer
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.kafka.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.varselbus.domain.DineSykmeldteHendelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.OffsetDateTime

class SendVarselService(
    val beskjedKafkaProducer: BeskjedKafkaProducer,
    val arbeidsgiverNotifikasjonProdusentConsumer: ArbeidsgiverNotifikasjonProdusentConsumer,
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val accessControl: AccessControl,
    val urlEnv: UrlEnv
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.db.SendVarselService")

    fun sendVarsel(pPlanlagtVarsel: PPlanlagtVarsel): String {
        // Recheck if user can be notified in case of recent 'Addressesperre'
        return try {
            val fodselnummer = accessControl.getFnrIfUserCanBeNotified(pPlanlagtVarsel.aktorId)
            val uuid = pPlanlagtVarsel.uuid
            fodselnummer?.let { fnr ->
                val varselUrl = varselUrlFromType(pPlanlagtVarsel.type)
                val varselContent = varselContentFromType(pPlanlagtVarsel.type)

                if (varselUrl !== null && varselContent !== null) {
                    when {
                        VarselType.AKTIVITETSKRAV.toString().equals(pPlanlagtVarsel.type) -> {
                            beskjedKafkaProducer.sendBeskjed(fnr, varselContent, uuid, varselUrl)

                            val dineSykmeldteVarsel = DineSykmeldteVarsel(
                                fnr,
                                pPlanlagtVarsel.orgnummer,
                                DineSykmeldteHendelseType.AKTIVITETSKRAV.toString(),
                                null,
                                DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST,
                                OffsetDateTime.now().plusWeeks(4L)
                            )

                            dineSykmeldteHendelseKafkaProducer.sendVarsel(dineSykmeldteVarsel)
                            arbeidsgiverNotifikasjonProdusentConsumer.createNewNotificationForArbeidsgiver(uuid, pPlanlagtVarsel.orgnummer, fnr)
                            pPlanlagtVarsel.type
                        }

                        VarselType.MER_VEILEDNING.toString().equals(pPlanlagtVarsel.type) -> {
                            beskjedKafkaProducer.sendBeskjed(fnr, varselContent, uuid, varselUrl)
                            pPlanlagtVarsel.type
                        }
                        else -> {
                            throw RuntimeException("Ukjent typestreng")
                        }
                    }
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
