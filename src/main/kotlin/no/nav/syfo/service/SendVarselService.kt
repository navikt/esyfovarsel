package no.nav.syfo.service

import no.nav.syfo.*
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.syfomotebehov.SyfoMotebehovConsumer
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType.AKTIVITETSKRAV
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING
import no.nav.syfo.db.domain.VarselType.SVAR_MOTEBEHOV
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
    val accessControlService: AccessControlService,
    val urlEnv: UrlEnv,
    val syfoMotebehovConsumer: SyfoMotebehovConsumer,
    val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    val journalpostdistribusjonConsumer: JournalpostdistribusjonConsumer,
    val dokarkivService: DokarkivService,
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.SendVarselService")

    val WEEKS_BEFORE_DELETE_AKTIVITETSKRAV = 2L

    suspend fun sendVarsel(pPlanlagtVarsel: PPlanlagtVarsel): String {
        // Recheck if user can be notified in case of recent 'Addressesperre'
        return try {
            val userAccessStatus = accessControlService.getUserAccessStatusByFnr(pPlanlagtVarsel.fnr)
            val uuid = pPlanlagtVarsel.uuid

            val varselUrl = varselUrlFromType(pPlanlagtVarsel.type)
            val varselContent = varselContentFromType(pPlanlagtVarsel.type)
            val orgnummer = pPlanlagtVarsel.orgnummer

            if (varselUrl !== null && varselContent !== null) {
                if (userSkalVarsles(pPlanlagtVarsel.type, userAccessStatus)) {
                    when (pPlanlagtVarsel.type) {
                        AKTIVITETSKRAV.name -> {
                            sendVarselTilSykmeldt(userAccessStatus.fnr!!, varselContent, uuid, varselUrl)
                            if (orgnummer !== null) {
                                sendAktivitetskravVarselTilArbeidsgiver(
                                    uuid,
                                    userAccessStatus.fnr!!,
                                    orgnummer
                                )
                            }
                            pPlanlagtVarsel.type
                        }
                        MER_VEILEDNING.name -> {
                            if (userAccessStatus.canUserBeDigitallyNotified) {
                                sendVarselTilSykmeldt(userAccessStatus.fnr!!, varselContent, uuid, varselUrl)
                                pPlanlagtVarsel.type
                            } else if(userAccessStatus.canUserBePhysicallyNotified) {
                                log.info("Skal sende fysisk brev for varsel med uuid: $uuid")
                                sendFysiskBrevTilReservertBruker(userAccessStatus.fnr!!, pPlanlagtVarsel.uuid)
                                pPlanlagtVarsel.type
                            } else {
                                log.info("Bruker med forespurt fnr er reservert eller gradert og kan ikke varsles ")
                                UTSENDING_FEILET
                            }
                        }
                        SVAR_MOTEBEHOV.name -> {
                            syfoMotebehovConsumer.sendVarselTilArbeidstaker(pPlanlagtVarsel.aktorId, pPlanlagtVarsel.fnr)
                            if (orgnummer !== null) {
                                val narmesteLederRelasjon = narmesteLederService.getNarmesteLederRelasjon(userAccessStatus.fnr!!, orgnummer)
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
                    log.info("Bruker med forespurt fnr er reservert eller gradert og kan ikke varsles ")
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

    private fun userSkalVarsles(varselType: String, userAccessStatus: UserAccessStatus): Boolean {
        return when (varselType) {
            AKTIVITETSKRAV.name -> {
                userAccessStatus.canUserBeDigitallyNotified
            }
            MER_VEILEDNING.name -> {
                userAccessStatus.canUserBeDigitallyNotified || userAccessStatus.canUserBePhysicallyNotified
            }
            SVAR_MOTEBEHOV.name -> {
                userAccessStatus.canUserBeDigitallyNotified
            }
            else -> {
                false
            }
        }
    }

    private suspend fun sendFysiskBrevTilReservertBruker(fnr: String, uuid: String) {
        val journalpostId = dokarkivService.getJournalpostId(fnr, uuid)
        log.info("Forsøkte å sende data til dokarkiv, journalpostId er $journalpostId, MER_VEILEDNING varsel med UUID: ${uuid}")

        val bestillingsId = journalpostId?.let { journalpostdistribusjonConsumer.distribuerJournalpost(it)?.bestillingsId }

        if (bestillingsId == null) {
            log.info("Forsøkte å sende PDF til print, men noe gikk galt, bestillingsId er null, MER_VEILEDNING varsel med UUID: ${uuid}")
        } else {
            log.info("Sendte PDF til print, bestillingsId er $bestillingsId, MER_VEILEDNING varsel med UUID: ${uuid}")
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
                { url: String -> ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_BODY_START + url + ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_BODY_END },
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
            AKTIVITETSKRAV.name -> "NAV skal vurdere aktivitetsplikten din"
            MER_VEILEDNING.name -> "Det nærmer seg datoen da du ikke lenger kan få sykepenger."
            SVAR_MOTEBEHOV.name -> "Ikke i bruk"
            else -> null
        }
    }

    private fun varselUrlFromType(type: String): URL? {
        val baseUrlSykInfo = urlEnv.baseUrlSykInfo
        val aktivitetskravUrl = URL(baseUrlSykInfo + "/aktivitetsplikt")
        val merVeiledningUrl = URL(baseUrlSykInfo + "/snart-slutt-pa-sykepengene")
        val svarMotebehovUrl = URL(baseUrlSykInfo + "/ikke-i-bruk")

        return when (type) {
            AKTIVITETSKRAV.name -> aktivitetskravUrl
            MER_VEILEDNING.name -> merVeiledningUrl
            SVAR_MOTEBEHOV.name -> svarMotebehovUrl
            else -> null
        }
    }
}
