package no.nav.syfo.service

import no.nav.syfo.*
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.syfomotebehov.SyfoMotebehovConsumer
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType.*
import no.nav.syfo.kafka.consumers.varselbus.domain.DineSykmeldteHendelseType
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class SendVarselService(
    val beskjedKafkaProducer: BeskjedKafkaProducer,
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val narmesteLederService: NarmesteLederService,
    val accessControl: AccessControl,
    val urlEnv: UrlEnv,
    val syfoMotebehovConsumer: SyfoMotebehovConsumer,
    val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.SendVarselService")
    val WEEKS_BEFORE_DELETE_AKTIVITETSKRAV = 2L

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
                        AKTIVITETSKRAV.toString().equals(pPlanlagtVarsel.type) -> {
                            sendVarselTilSykmeldt(fnr, varselContent, uuid, varselUrl)
                            if (orgnummer !== null) {
                                sendAktivitetskravVarselTilArbeidsgiver(
                                    uuid,
                                    fnr,
                                    orgnummer
                                )
                            }
                            pPlanlagtVarsel.type
                        }

                        MER_VEILEDNING.toString().equals(pPlanlagtVarsel.type) -> {
                            sendVarselTilSykmeldt(fnr, varselContent, uuid, varselUrl)
                            pPlanlagtVarsel.type
                        }

                        SVAR_MOTEBEHOV.toString().equals(pPlanlagtVarsel.type) -> {
                            syfoMotebehovConsumer.sendVarselTilArbeidstaker(pPlanlagtVarsel.aktorId, pPlanlagtVarsel.fnr)
                            if (orgnummer !== null) {
                                val narmesteLederRelasjon = narmesteLederService.getNarmesteLederRelasjon(fnr, orgnummer)
                                if (narmesteLederService.hasNarmesteLederInfo(narmesteLederRelasjon)) {
                                    syfoMotebehovConsumer.sendVarselTilNaermesteLeder(
                                        pPlanlagtVarsel.aktorId,
                                        orgnummer,
                                        narmesteLederRelasjon!!.narmesteLederFnr!!,
                                        pPlanlagtVarsel.fnr
                                    )
                                }
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
            } ?: run {
                log.info("Bruker med forespurt fnr er reservert eller gradert og kan ikke varsles ")
                return UTSENDING_FEILET
            }
        } catch (e: RuntimeException) {
            log.error("Feil i utsending av varsel med UUID: ${pPlanlagtVarsel.uuid} | ${e.message}", e)
            UTSENDING_FEILET
        }
    }

    private fun sendAktivitetskravVarselTilArbeidsgiver(uuid: String, arbeidstakerFnr: String, orgnummer: String) {
        val dineSykmeldteVarsel = DineSykmeldteVarsel(
            ansattFnr = arbeidstakerFnr,
            orgnr = orgnummer,
            oppgavetype = DineSykmeldteHendelseType.AKTIVITETSKRAV.toString(),
            lenke = null,
            tekst = DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST,
            utlopstidspunkt = OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE_AKTIVITETSKRAV)
        )

        log.info("Sender AKTIVITETSKRAV varsel til Dine sykmeldte for uuid $uuid")
        dineSykmeldteHendelseKafkaProducer.sendVarsel(dineSykmeldteVarsel)

        log.info("Sender AKTIVITETSKRAV varsel til Arbeidsgivernotifikasjoner for uuid $uuid")
        arbeidsgiverNotifikasjonService.sendNotifikasjon(
            ArbeidsgiverNotifikasjonInput(
                UUID.fromString(uuid),
                orgnummer,
                null,
                arbeidstakerFnr,
                ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_MESSAGE_TEXT,
                ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_TITLE,
                {url: String -> ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_BODY_START + url + ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_BODY_END},
                LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE_AKTIVITETSKRAV)
            )
        )
    }

    private fun sendVarselTilSykmeldt(fnr: String, varselContent: String, uuid: String, varselUrl: URL) {
        log.info("Sender varsel til Brukernotifikasjoner for uuid $uuid")
        beskjedKafkaProducer.sendBeskjed(fnr, varselContent, uuid, varselUrl)
        log.info("Har sendt varsel til Brukernotifikasjoner for uuid $uuid")
    }

    private fun varselContentFromType(type: String): String? {
        return when (type) {
            AKTIVITETSKRAV.toString() -> "NAV skal vurdere aktivitetsplikten din"
            MER_VEILEDNING.toString() -> "Det nærmer seg datoen da du ikke lenger kan få sykepenger."
            SVAR_MOTEBEHOV.toString() -> "Ikke i bruk"
            else -> null
        }
    }

    private fun varselUrlFromType(type: String): URL? {
        val baseUrlSykInfo = urlEnv.baseUrlSykInfo
        val aktivitetskravUrl = URL(baseUrlSykInfo + "/aktivitetsplikt")
        val merVeiledningUrl = URL(baseUrlSykInfo + "/snart-slutt-pa-sykepengene")
        val svarMotebehovUrl = URL(baseUrlSykInfo + "/ikke-i-bruk")

        return when (type) {
            AKTIVITETSKRAV.toString() -> aktivitetskravUrl
            MER_VEILEDNING.toString() -> merVeiledningUrl
            SVAR_MOTEBEHOV.toString() -> svarMotebehovUrl
            else -> null
        }
    }
}
