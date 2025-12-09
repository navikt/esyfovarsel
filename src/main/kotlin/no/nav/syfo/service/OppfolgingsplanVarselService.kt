package no.nav.syfo.service

import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_FORESPORSEL_EMAIL_BODY
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_FORESPORSEL_EMAIL_TITLE
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_FORESPORSEL_MESSAGE_TEXT
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_EMAIL_BODY
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_EMAIL_TITLE
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_MESSAGE_TEXT
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP
import no.nav.syfo.BRUKERNOTIFIKASJONER_OPPFOLGINGSPLANER_SYKMELDT_URL
import no.nav.syfo.BRUKERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_MESSAGE_TEXT
import no.nav.syfo.DINE_SYKMELDTE_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.pdl.PdlClient
import no.nav.syfo.consumer.pdl.fullName
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.SakStatus
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.BESKJED
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class OppfolgingsplanVarselService(
    private val senderFacade: SenderFacade,
    private val accessControlService: AccessControlService,
    private val oppfolgingsplanerUrl: String,
    private val narmesteLederService: NarmesteLederService,
    private val pdlClient: PdlClient,
) {
    companion object {
        private const val WEEKS_BEFORE_DELETE = 4L
        private val log = LoggerFactory.getLogger(OppfolgingsplanVarselService::class.qualifiedName)
    }

    suspend fun sendVarselTilArbeidstaker(
        varselHendelse: ArbeidstakerHendelse
    ) {
        val eksternVarsling = accessControlService.canUserBeNotifiedByEmailOrSMS(varselHendelse.arbeidstakerFnr)
        varsleArbeidstakerViaBrukernotifikasjoner(
            varselHendelse = varselHendelse,
            eksternVarsling = eksternVarsling,
        )
    }

    suspend fun sendVarselTilNarmesteLeder(
        varselHendelse: NarmesteLederHendelse
    ) {
        senderFacade.sendTilDineSykmeldte(
            varselHendelse,
            DineSykmeldteVarsel(
                ansattFnr = varselHendelse.arbeidstakerFnr,
                orgnr = varselHendelse.orgnummer,
                oppgavetype = varselHendelse.type.toDineSykmeldteHendelseType().toString(),
                lenke = null,
                tekst = DINE_SYKMELDTE_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST,
                utlopstidspunkt = OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)
            )
        )
        senderFacade.sendTilArbeidsgiverNotifikasjon(
            varselHendelse,
            ArbeidsgiverNotifikasjonInput(
                UUID.randomUUID(),
                varselHendelse.orgnummer,
                varselHendelse.narmesteLederFnr,
                varselHendelse.arbeidstakerFnr,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_MESSAGE_TEXT,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_EMAIL_TITLE,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_EMAIL_BODY,
                LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                meldingstype = Meldingstype.BESKJED,
                grupperingsid = UUID.randomUUID().toString(),
            )
        )
    }

    suspend fun sendOppfolgingsplanForesporselVarselTilNarmesteLeder(
        varselHendelse: NarmesteLederHendelse
    ) {
        log.info("Send oppfolgingsplan foresporsel-varsel til narmeste leder")
        val narmesteLederRelasjon = narmesteLederService.getNarmesteLederRelasjon(
            varselHendelse.arbeidstakerFnr,
            varselHendelse.orgnummer
        )

        if (narmesteLederRelasjon?.narmesteLederId == null) {
            log.error("Sender ikke varsel: narmesteLederRelasjon er null, eller mangler narmesteLederId")
            return
        }

        if (narmesteLederRelasjon.narmesteLederEpost == null) {
            log.error("Sender ikke varsel: narmesteLederRelasjon mangler epost")
            return
        }

        val url = "$oppfolgingsplanerUrl/arbeidsgiver/${narmesteLederRelasjon.narmesteLederId}"
        val personData = pdlClient.hentPerson(personIdent = varselHendelse.arbeidstakerFnr)
        val grupperingsid = UUID.randomUUID().toString()

        val sakInput = NySakInput(
            grupperingsid = grupperingsid,
            narmestelederId = narmesteLederRelasjon.narmesteLederId,
            merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
            virksomhetsnummer = varselHendelse.orgnummer,
            narmesteLederFnr = varselHendelse.narmesteLederFnr,
            ansattFnr = varselHendelse.arbeidstakerFnr,
            tittel = personData?.fullName()?.let { "Oppfølging av $it" } ?: "Oppfølging av sykmeldt",
            lenke = url,
            initiellStatus = SakStatus.MOTTATT,
            hardDeleteDate = LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
        )

        senderFacade.createNewSak(sakInput)
        senderFacade.sendTilArbeidsgiverNotifikasjon(
            varselHendelse,
            ArbeidsgiverNotifikasjonInput(
                UUID.randomUUID(),
                varselHendelse.orgnummer,
                varselHendelse.narmesteLederFnr,
                varselHendelse.arbeidstakerFnr,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_FORESPORSEL_MESSAGE_TEXT,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_FORESPORSEL_EMAIL_TITLE,
                ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_FORESPORSEL_EMAIL_BODY,
                LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                Meldingstype.BESKJED,
                grupperingsid,
                url
            )
        )
    }

    private fun varsleArbeidstakerViaBrukernotifikasjoner(
        varselHendelse: ArbeidstakerHendelse,
        eksternVarsling: Boolean,
    ) {
        senderFacade.sendTilBrukernotifikasjoner(
            uuid = UUID.randomUUID().toString(),
            mottakerFnr = varselHendelse.arbeidstakerFnr,
            content = getMessageText(varselHendelse) ?: BRUKERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_MESSAGE_TEXT,
            url = URI(oppfolgingsplanerUrl + BRUKERNOTIFIKASJONER_OPPFOLGINGSPLANER_SYKMELDT_URL).toURL(),
            arbeidstakerFnr = varselHendelse.arbeidstakerFnr,
            orgnummer = varselHendelse.orgnummer,
            hendelseType = varselHendelse.type.name,
            eksternVarsling = eksternVarsling,
            varseltype = BESKJED
        )
    }

    fun getMessageText(arbeidstakerHendelse: ArbeidstakerHendelse): String? =
        when (arbeidstakerHendelse.type) {
            SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> BRUKERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_MESSAGE_TEXT
            else -> {
                log.error(
                    "Klarte ikke mappe varsel av type ${arbeidstakerHendelse.type} ved mapping av hendelsetype " +
                            "til oppfolgingsplanVarsel melding tekst"
                )
                null
            }
        }
}
