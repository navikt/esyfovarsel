package no.nav.syfo.service

import no.nav.syfo.DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST
import no.nav.syfo.UrlEnv
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.syfomotebehov.SyfoMotebehovConsumer
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.consumers.varselbus.domain.DineSykmeldteHendelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime

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
                        VarselType.AKTIVITETSKRAV.toString().equals(pPlanlagtVarsel.type) -> {
                            if (uuid != "66705dc4-e3b6-4afd-ab1a-ccd08bbcbc78" &&
                                uuid != "b2dec22e-4c5d-49f0-8e53-8ed06dea68e5"
                            ) {
                                sendVarselTilSykmeldt(fnr, varselContent, uuid, varselUrl)
                            }
                            if (orgnummer !== null) {
                                log.info("Henter NL-relasjon for UUID: $uuid")
                                val narmesteLederRelasjon = narmesteLederService.getNarmesteLederRelasjon(fnr, orgnummer)
                                log.info("NL-relasjon hentet for UUID: $uuid")
                                if (narmesteLederService.hasNarmesteLederInfo(narmesteLederRelasjon)) {
                                    sendAktivitetskravVarselTilArbeidsgiver(
                                        fnr,
                                        orgnummer,
                                        uuid,
                                        narmesteLederRelasjon!!.narmesteLederFnr!!,
                                        narmesteLederRelasjon.narmesteLederEpost!!
                                    )
                                }
                            }
                            pPlanlagtVarsel.type
                        }

                        VarselType.MER_VEILEDNING.toString().equals(pPlanlagtVarsel.type) -> {
                            sendVarselTilSykmeldt(fnr, varselContent, uuid, varselUrl)
                            pPlanlagtVarsel.type
                        }

                        VarselType.SVAR_MOTEBEHOV.toString().equals(pPlanlagtVarsel.type) -> {
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

    private suspend fun sendAktivitetskravVarselTilArbeidsgiver(fnr: String, orgnummer: String, uuid: String, narmesteLederFnr: String, narmesteLederEpostadresse: String) {
        val dineSykmeldteVarsel = DineSykmeldteVarsel(
            fnr,
            orgnummer,
            DineSykmeldteHendelseType.AKTIVITETSKRAV.toString(),
            null,
            DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST,
            OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE_AKTIVITETSKRAV)
        )

        val nlRelasjon = narmesteLederService.getNarmesteLederRelasjon(fnr, orgnummer)
        val dineSykmeldteUrlSuffix = when {
            nlRelasjon !== null && narmesteLederService.hasNarmesteLederInfo(nlRelasjon) -> "/${nlRelasjon.narmesteLederId}"
            else -> {
                log.warn("Lenker til Dine sykmeldte landingsside: narmesteLederRelasjon er null eller har ikke kontaktinfo")
                ""
            }
        }

        log.info("Sender AKTIVITETSKRAV varsel til Dine sykmeldte for uuid $uuid")
        dineSykmeldteHendelseKafkaProducer.sendVarsel(dineSykmeldteVarsel)

        log.info("Sender AKTIVITETSKRAV varsel til Arbeidsgivernotifikasjoner for uuid $uuid")
        arbeidsgiverNotifikasjonService.sendNotifikasjon(
            VarselType.AKTIVITETSKRAV,
            uuid,
            orgnummer,
            "${urlEnv.baseUrlDineSykmeldte}$dineSykmeldteUrlSuffix",
            narmesteLederFnr,
            fnr,
            narmesteLederEpostadresse,
            LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE_AKTIVITETSKRAV),
        )
    }

    private fun sendVarselTilSykmeldt(fnr: String, varselContent: String, uuid: String, varselUrl: URL) {
        log.info("Sender varsel til Brukernotifikasjoner for uuid $uuid")
        beskjedKafkaProducer.sendBeskjed(fnr, varselContent, uuid, varselUrl)
        log.info("Har sendt varsel til Brukernotifikasjoner for uuid $uuid")
    }

    private fun varselContentFromType(type: String): String? {
        return when (type) {
            VarselType.AKTIVITETSKRAV.toString() -> "NAV skal vurdere aktivitetsplikten din"
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
