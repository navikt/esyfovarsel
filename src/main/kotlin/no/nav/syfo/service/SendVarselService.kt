package no.nav.syfo.service

import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period
import java.util.*
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_BODY
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_TITLE
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_MESSAGE_TEXT
import no.nav.syfo.DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST
import no.nav.syfo.UrlEnv
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.pdl.getFodselsdato
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType.AKTIVITETSKRAV
import no.nav.syfo.db.domain.VarselType.MER_VEILEDNING
import no.nav.syfo.db.domain.VarselType.SVAR_MOTEBEHOV
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.DineSykmeldteHendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.utils.parsePDLDate
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SendVarselService(
    val beskjedKafkaProducer: BeskjedKafkaProducer,
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val accessControlService: AccessControlService,
    val urlEnv: UrlEnv,
    val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    val merVeiledningVarselService: MerVeiledningVarselService,
    val sykmeldingService: SykmeldingService,
    val pdlConsumer: PdlConsumer
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.SendVarselService")

    val WEEKS_BEFORE_DELETE_AKTIVITETSKRAV = 2L

    suspend fun sendVarsel(pPlanlagtVarsel: PPlanlagtVarsel): String {
        // Recheck if user can be notified in case of recent 'Addressesperre'
        return try {
            val userAccessStatus = accessControlService.getUserAccessStatus(pPlanlagtVarsel.fnr)
            val fnr = userAccessStatus.fnr!!
            val uuid = pPlanlagtVarsel.uuid
            val birthDate = pdlConsumer.hentPerson(fnr)?.getFodselsdato()?.let { parsePDLDate(it) }
            log.info("[FODSELSDATO] Parsed fodselsdato: ${birthDate}")

            val varselUrl = varselUrlFromType(pPlanlagtVarsel.type)
            val varselContent = varselContentFromType(pPlanlagtVarsel.type)
            val orgnummer = pPlanlagtVarsel.orgnummer

            if (varselUrl !== null && varselContent !== null) {
                if (userSkalVarsles(pPlanlagtVarsel.type, userAccessStatus, birthDate)) {
                    when (pPlanlagtVarsel.type) {
                        AKTIVITETSKRAV.name -> {
                            val sykmeldingStatus =
                                sykmeldingService.checkSykmeldingStatusForVirksomhet(pPlanlagtVarsel.utsendingsdato, fnr, orgnummer)

                            sendVarselTilSykmeldt(fnr, uuid, varselContent, varselUrl)

                            if (sykmeldingStatus.sendtArbeidsgiver) {
                                sendAktivitetskravVarselTilArbeidsgiver(
                                    uuid,
                                    fnr,
                                    orgnummer!!
                                )
                            } else {
                                log.info("Sender ikke varsel om aktivitetskrav til AG da sykmelding ikke er sendt AG")
                            }
                            pPlanlagtVarsel.type

                        }

                        MER_VEILEDNING.name -> {
                            sendMerVeiledningVarselTilArbeidstaker(pPlanlagtVarsel, userAccessStatus)
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

    private fun userSkalVarsles(varselType: String, userAccessStatus: UserAccessStatus, birthDate: LocalDate?): Boolean {
        log.info("[${varselType}] - userAccessStatus.canUserBeDigitallyNotified: ${userAccessStatus.canUserBeDigitallyNotified} | userAccessStatus.canUserBePhysicallyNotified: ${userAccessStatus.canUserBePhysicallyNotified}")
        return when (varselType) {
            AKTIVITETSKRAV.name -> {
                userAccessStatus.canUserBeDigitallyNotified
            }

            MER_VEILEDNING.name -> {
                (userAccessStatus.canUserBeDigitallyNotified || userAccessStatus.canUserBePhysicallyNotified) && isUserYoungerThan67(birthDate)
            }

            SVAR_MOTEBEHOV.name -> {
                userAccessStatus.canUserBeDigitallyNotified
            }

            else -> {
                false
            }
        }
    }

    private fun isUserYoungerThan67(birthDate: LocalDate?): Boolean {
        return birthDate == null || (Period.between(birthDate, LocalDate.now()).years < 67)
    }

    private fun sendAktivitetskravVarselTilArbeidsgiver(
        uuid: String,
        arbeidstakerFnr: String,
        orgnummer: String
    ) {
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
                ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_BODY,
                LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE_AKTIVITETSKRAV)
            )
        )
    }

    private fun sendMerVeiledningVarselTilArbeidstaker(
        pPlanlagtVarsel: PPlanlagtVarsel,
        userAccessStatus: UserAccessStatus
    ) {
        merVeiledningVarselService.sendVarselTilArbeidstaker(
            ArbeidstakerHendelse(
                HendelseType.SM_MER_VEILEDNING,
                null,
                pPlanlagtVarsel.fnr,
                null
            ),
            pPlanlagtVarsel.uuid,
            userAccessStatus
        )
    }

    private fun sendVarselTilSykmeldt(
        fnr: String,
        uuid: String,
        varselContent: String,
        varselUrl: URL
    ) {
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
