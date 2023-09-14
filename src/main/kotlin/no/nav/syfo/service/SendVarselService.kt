package no.nav.syfo.service

import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_BODY
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_TITLE
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_MESSAGE_TEXT
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP
import no.nav.syfo.DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST
import no.nav.syfo.UrlEnv
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UTSENDING_FEILET
import no.nav.syfo.db.domain.VarselType.*
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.DineSykmeldteHendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class SendVarselService(
    val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val accessControlService: AccessControlService,
    val urlEnv: UrlEnv,
    val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    val merVeiledningVarselService: MerVeiledningVarselService,
    val sykmeldingService: SykmeldingService,
    val aktivitetskravVarselFinder: AktivitetskravVarselFinder,
    val merVeiledningVarselFinder: MerVeiledningVarselFinder,
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.SendVarselService")

    val WEEKS_BEFORE_DELETE_AKTIVITETSKRAV = 2L

    suspend fun sendVarsel(pPlanlagtVarsel: PPlanlagtVarsel): String {
        return try {
            val userAccessStatus = accessControlService.getUserAccessStatus(pPlanlagtVarsel.fnr)
            val fnr = userAccessStatus.fnr!!
            val uuid = pPlanlagtVarsel.uuid

            val varselUrl = varselUrlFromType(pPlanlagtVarsel.type)
            val varselContent = varselContentFromType(pPlanlagtVarsel.type)
            val orgnummer = pPlanlagtVarsel.orgnummer

            if (varselUrl !== null && varselContent !== null) {
                if (userSkalVarsles(pPlanlagtVarsel.type)) {
                    when (pPlanlagtVarsel.type) {
                        AKTIVITETSKRAV.name -> {
                            if (aktivitetskravVarselFinder.isBrukerYngreEnn70Ar(fnr)) {
                                val sykmeldingStatus =
                                    sykmeldingService.checkSykmeldingStatusForVirksomhet(
                                        pPlanlagtVarsel.utsendingsdato,
                                        fnr,
                                        orgnummer,
                                    )

                                sendVarselTilSykmeldt(fnr, uuid, varselContent, varselUrl)

                                if (sykmeldingStatus.sendtArbeidsgiver) {
                                    sendAktivitetskravVarselTilArbeidsgiver(
                                        uuid,
                                        fnr,
                                        orgnummer!!,
                                    )
                                } else {
                                    log.info("Sender ikke varsel om aktivitetskrav til AG da sykmelding ikke er sendt AG")
                                }
                            }
                            pPlanlagtVarsel.type
                        }

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
        return varselType == AKTIVITETSKRAV.name || varselType == MER_VEILEDNING.name
    }

    private fun sendAktivitetskravVarselTilArbeidsgiver(
        uuid: String,
        arbeidstakerFnr: String,
        orgnummer: String,
    ) {
        val dineSykmeldteVarsel = DineSykmeldteVarsel(
            ansattFnr = arbeidstakerFnr,
            orgnr = orgnummer,
            oppgavetype = DineSykmeldteHendelseType.AKTIVITETSKRAV.toString(),
            lenke = null,
            tekst = DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST,
            utlopstidspunkt = OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE_AKTIVITETSKRAV),
        )

        dineSykmeldteHendelseKafkaProducer.sendVarsel(dineSykmeldteVarsel)

        log.info("Sender AKTIVITETSKRAV varsel til Arbeidsgivernotifikasjoner for uuid $uuid")
        arbeidsgiverNotifikasjonService.sendNotifikasjon(
            ArbeidsgiverNotifikasjonInput(
                UUID.fromString(uuid),
                orgnummer,
                null,
                arbeidstakerFnr,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_MESSAGE_TEXT,
                ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_TITLE,
                ARBEIDSGIVERNOTIFIKASJON_AKTIVITETSKRAV_EMAIL_BODY,
                LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE_AKTIVITETSKRAV),
            ),
        )
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

    private fun sendVarselTilSykmeldt(
        fnr: String,
        uuid: String,
        varselContent: String,
        varselUrl: URL,
    ) {
        log.info("Sender varsel til Brukernotifikasjoner for uuid $uuid")
        brukernotifikasjonKafkaProducer.sendBeskjed(fnr, varselContent, uuid, varselUrl, eksternVarsling = true)
        log.info("Har sendt varsel til Brukernotifikasjoner for uuid $uuid")
    }

    private fun varselContentFromType(type: String): String? {
        return when (type) {
            AKTIVITETSKRAV.name -> "NAV skal vurdere aktivitetsplikten din"
            MER_VEILEDNING.name -> "Det nærmer seg datoen da du ikke lenger kan få sykepenger."
            else -> null
        }
    }

    private fun varselUrlFromType(type: String): URL? {
        val baseUrlSykInfo = urlEnv.baseUrlSykInfo
        val aktivitetskravUrl = URL(baseUrlSykInfo + "/aktivitetsplikt")
        val merVeiledningUrl = URL(baseUrlSykInfo + "/snart-slutt-pa-sykepengene")

        return when (type) {
            AKTIVITETSKRAV.name -> aktivitetskravUrl
            MER_VEILEDNING.name -> merVeiledningUrl
            else -> null
        }
    }
}
