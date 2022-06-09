package no.nav.syfo.service

import no.nav.syfo.DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST
import no.nav.syfo.UrlEnv
import no.nav.syfo.consumer.arbeidsgiverNotifikasjonProdusent.ArbeidsgiverNotifikasjonProdusent
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.syfomotebehov.SyfoMotebehovConsumer
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
    val arbeidsgiverNotifikasjonProdusent: ArbeidsgiverNotifikasjonProdusent,
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val narmesteLederService: NarmesteLederService,
    val accessControl: AccessControl,
    val urlEnv: UrlEnv,
    val syfoMotebehovConsumer: SyfoMotebehovConsumer
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.db.SendVarselService")

    suspend fun sendVarsel(pPlanlagtVarsel: PPlanlagtVarsel): String {
        // Recheck if user can be notified in case of recent 'Addressesperre'
        return try {
            val fodselnummer = accessControl.getFnrIfUserCanBeNotified(pPlanlagtVarsel.aktorId)
            val uuid = pPlanlagtVarsel.uuid
            fodselnummer?.let { fnr ->
                val varselUrl = varselUrlFromType(pPlanlagtVarsel.type)
                val varselContent = varselContentFromType(pPlanlagtVarsel.type)
                val orgnummer = pPlanlagtVarsel.orgnummer

                if (varselUrl !== null && varselContent !== null) {
                    when {
                        VarselType.AKTIVITETSKRAV.toString().equals(pPlanlagtVarsel.type) -> {
                            if (uuid != "66705dc4-e3b6-4afd-ab1a-ccd08bbcbc78"
                                && uuid != "b2dec22e-4c5d-49f0-8e53-8ed06dea68e5") {
                                sendVarselTilSykmeldt(fnr, varselContent, uuid, varselUrl)
                            }
                            if (orgnummer !== null) {
                                val narmesteLederRelasjon = narmesteLederService.getNarmesteLederRelasjon(fnr, orgnummer, uuid)
                                if (narmesteLederService.hasNarmesteLederInfo(narmesteLederRelasjon)) {
                                    sendVarselTilArbeidsgiver(fnr, orgnummer, uuid, narmesteLederRelasjon!!.narmesteLederFnr!!, narmesteLederRelasjon.narmesteLederEpost!!)
                                }
                            }
                            pPlanlagtVarsel.type
                        }

                        VarselType.MER_VEILEDNING.toString().equals(pPlanlagtVarsel.type) -> {
                            sendVarselTilSykmeldt(fnr, varselContent, uuid, varselUrl)
                            pPlanlagtVarsel.type
                        }

                        VarselType.SVAR_MOTEBEHOV.toString().equals(pPlanlagtVarsel.type) -> {
                            if (orgnummer !== null) {
                                syfoMotebehovConsumer.sendVarselTilNaermesteLeder(pPlanlagtVarsel.aktorId, orgnummer)

                            }
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

    private fun sendVarselTilArbeidsgiver(fnr: String, orgnummer: String, uuid: String, narmesteLederFnr: String, narmesteLederEpostadresse: String) {
        val dineSykmeldteVarsel = DineSykmeldteVarsel(
            fnr,
            orgnummer,
            DineSykmeldteHendelseType.AKTIVITETSKRAV.toString(),
            null,
            DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST,
            OffsetDateTime.now().plusWeeks(4L)
        )

        log.info("Sender varsel til Dine sykmeldte for uuid $uuid")
        dineSykmeldteHendelseKafkaProducer.sendVarsel(dineSykmeldteVarsel)
        log.info("Sender varsel til Arbeidsgivernotifikasjoner for uuid $uuid")
        arbeidsgiverNotifikasjonProdusent.createNewNotificationForArbeidsgiver(uuid, orgnummer, fnr, narmesteLederFnr, narmesteLederEpostadresse)
    }

    private fun sendVarselTilSykmeldt(fnr: String, varselContent: String, uuid: String, varselUrl: URL) {
        log.info("Sender varsel til Brukernotifikasjoner for uuid $uuid")
        beskjedKafkaProducer.sendBeskjed(fnr, varselContent, uuid, varselUrl)
        log.info("Har sendt varsel til Brukernotifikasjoner for uuid $uuid")
    }

    private fun varselContentFromType(type: String): String? {
        return when (type) {
            VarselType.AKTIVITETSKRAV.toString() -> "NAV skal nå vurdere aktivitetsplikten din"
            VarselType.MER_VEILEDNING.toString() -> "Det nærmer seg datoen da du ikke lenger kan få sykepenger."
            VarselType.SVAR_MOTEBEHOV.toString() -> "Ikke i bruk"
            else -> null
        }
    }

    private fun varselUrlFromType(type: String): URL? {
        val baseUrlSykInfo = urlEnv.baseUrlSykInfo
        val aktivitetskravUrl = URL(baseUrlSykInfo + "/aktivitetsplikt")
        val merVeiledningUrl = URL(baseUrlSykInfo + "/snart-slutt-pa-sykepengene")
        val svarMotebehovUrl = URL(baseUrlSykInfo + "/ikke-i-bruk")

        return when (type) {
            VarselType.AKTIVITETSKRAV.toString() -> aktivitetskravUrl
            VarselType.MER_VEILEDNING.toString() -> merVeiledningUrl
            VarselType.SVAR_MOTEBEHOV.toString() -> svarMotebehovUrl
            else -> null
        }
    }
}
