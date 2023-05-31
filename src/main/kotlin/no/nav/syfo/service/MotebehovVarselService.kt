package no.nav.syfo.service

import kotlinx.coroutines.runBlocking
import no.nav.syfo.*
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer.MeldingType.OPPGAVE
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

const val DITT_SYKEFRAVAER_HENDELSE_TYPE_DIALOGMOTE_SVAR_MOTEBEHOV = "ESYFOVARSEL_DIALOGMOTE_SVAR_MOTEBEHOV"

class MotebehovVarselService(
    val senderFacade: SenderFacade,
    dialogmoterUrl: String,
    val sykmeldingService: SykmeldingService,
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
        val url = URL(svarMotebehovUrl)
        senderFacade.sendTilBrukernotifikasjoner(
            UUID.randomUUID().toString(),
            varselHendelse.arbeidstakerFnr,
            BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST,
            url,
            varselHendelse,
            OPPGAVE,
        )
    }

    private fun sendOppgaveTilDittSykefravaer(
        arbeidstakerHendelse: ArbeidstakerHendelse
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
            )
        )
    }
}
