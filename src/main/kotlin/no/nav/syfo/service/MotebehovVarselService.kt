package no.nav.syfo.service

import kotlinx.coroutines.runBlocking
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_EMAIL_BODY
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_EMAIL_TITLE
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_MESSAGE_TEXT
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
import no.nav.syfo.DINE_SYKMELDTE_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
import no.nav.syfo.DITT_SYKEFRAVAER_DIALOGMOTE_SVAR_MOTEBEHOV_MESSAGE_TEXT
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataMotebehovTilbakemelding
import no.nav.syfo.kafka.consumers.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer.MeldingType.OPPGAVE
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import org.apache.commons.cli.MissingArgumentException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

const val DITT_SYKEFRAVAER_HENDELSE_TYPE_DIALOGMOTE_SVAR_MOTEBEHOV = "ESYFOVARSEL_DIALOGMOTE_SVAR_MOTEBEHOV"

class MotebehovVarselService(
    val senderFacade: SenderFacade,
    val accessControlService: AccessControlService,
    val sykmeldingService: SykmeldingService,
    dialogmoterUrl: String,
) {
    val WEEKS_BEFORE_DELETE = 4L
    private val log: Logger = LoggerFactory.getLogger(MotebehovVarselService::class.qualifiedName)
    private val svarMotebehovUrl: String = "$dialogmoterUrl/sykmeldt/motebehov/svar"

    fun sendVarselTilNarmesteLeder(varselHendelse: NarmesteLederHendelse) {
        runBlocking {
            // Quickfix for å unngå å sende varsel til bedrifter der bruker ikke er sykmeldt. Det kan skje når den sykmeldte har vært sykmeldt fra flere arbeidsforhold,
            // men bare er sykmeldt ved én av dem nå
            val sykmeldingStatusForVirksomhet =
                sykmeldingService.checkSykmeldingStatusForVirksomhet(LocalDate.now(), varselHendelse.arbeidstakerFnr, varselHendelse.orgnummer)

            if (sykmeldingStatusForVirksomhet.sendtArbeidsgiver) {
                sendVarselTilDineSykmeldte(varselHendelse)
                sendVarselTilArbeidsgiverNotifikasjon(varselHendelse)
            } else {
                log.info("[MotebehovVarselService]: Sender ikke Svar møtebehov-varsel til NL fordi arbeidstaker ikke er sykmeldt fra bedriften")
            }
        }
    }

    fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        sendVarselTilBrukernotifikasjoner(varselHendelse)
        sendOppgaveTilDittSykefravaer(varselHendelse)
    }

    private fun sendVarselTilArbeidsgiverNotifikasjon(varselHendelse: NarmesteLederHendelse) {
        senderFacade.sendTilArbeidsgiverNotifikasjon(
            varselHendelse,
            ArbeidsgiverNotifikasjonInput(
                UUID.randomUUID(),
                varselHendelse.orgnummer,
                varselHendelse.narmesteLederFnr,
                varselHendelse.arbeidstakerFnr,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_MESSAGE_TEXT,
                ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_EMAIL_TITLE,
                ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_EMAIL_BODY,
                LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                Meldingstype.OPPGAVE,
            ),
        )
    }

    private fun sendVarselTilDineSykmeldte(varselHendelse: NarmesteLederHendelse) {
        val varseltekst = DINE_SYKMELDTE_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
        val dineSykmeldteVarsel = DineSykmeldteVarsel(
            ansattFnr = varselHendelse.arbeidstakerFnr,
            orgnr = varselHendelse.orgnummer,
            oppgavetype = varselHendelse.type.toDineSykmeldteHendelseType().toString(),
            lenke = null,
            tekst = varseltekst,
            utlopstidspunkt = OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
        )
        senderFacade.sendTilDineSykmeldte(varselHendelse, dineSykmeldteVarsel)
    }

    private fun sendVarselTilBrukernotifikasjoner(varselHendelse: ArbeidstakerHendelse) {
        val fnr = varselHendelse.arbeidstakerFnr
        val eksternVarsling = accessControlService.canUserBeNotifiedByEmailOrSMS(fnr)
        val url = URL(svarMotebehovUrl)
        senderFacade.sendTilBrukernotifikasjoner(
            UUID.randomUUID().toString(),
            varselHendelse.arbeidstakerFnr,
            BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST,
            url,
            varselHendelse,
            OPPGAVE,
            eksternVarsling
        )
    }

    private fun sendOppgaveTilDittSykefravaer(
        arbeidstakerHendelse: ArbeidstakerHendelse,
    ) {
        val melding = DittSykefravaerMelding(
            OpprettMelding(
                DITT_SYKEFRAVAER_DIALOGMOTE_SVAR_MOTEBEHOV_MESSAGE_TEXT,
                svarMotebehovUrl,
                Variant.INFO,
                true,
                DITT_SYKEFRAVAER_HENDELSE_TYPE_DIALOGMOTE_SVAR_MOTEBEHOV,
                OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE).toInstant(),
            ),
            null,
            arbeidstakerHendelse.arbeidstakerFnr,
        )
        senderFacade.sendTilDittSykefravaer(
            arbeidstakerHendelse,
            DittSykefravaerVarsel(
                UUID.randomUUID().toString(),
                melding,
            ),
        )
    }

    fun sendMotebehovBeskjedTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        val fnr = varselHendelse.arbeidstakerFnr
        val eksternVarsling = accessControlService.canUserBeNotifiedByEmailOrSMS(fnr)
        val data = dataToVarselDataMotebehovTilbakemelding(varselHendelse.data)
        senderFacade.sendTilBrukernotifikasjoner(
            uuid = UUID.randomUUID().toString(),
            mottakerFnr = varselHendelse.arbeidstakerFnr,
            content = data.tilbakemelding,
            varselHendelse = varselHendelse,
            eksternVarsling = eksternVarsling
        )
    }

    fun dataToVarselDataMotebehovTilbakemelding(data: Any?): VarselDataMotebehovTilbakemelding {
        return data?.let {
            val varselData = createObjectMapper().readValue(
                this.toString(),
                VarselDataMotebehovTilbakemelding::class.java,
            )
            varselData
                ?: throw IOException("VarselDataMotebehovBeskjed har feil format")
        } ?: throw MissingArgumentException("EsyfovarselHendelse mangler 'data'-felt")
    }
}
