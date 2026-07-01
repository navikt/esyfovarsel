package no.nav.syfo.service

import com.fasterxml.jackson.databind.JsonNode
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
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.db.domain.toNarmesteLederHendelse
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.OppfolgingsplanVarselbestillingData
import no.nav.syfo.kafka.consumers.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.toOppfolgingsplanVarselbestillingData
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakNarmesteLederInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.SakStatus
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.BESKJED
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

class OppfolgingsplanVarselService(
    private val senderFacade: SenderFacade,
    private val accessControlService: AccessControlService,
    private val oppfolgingsplanerUrl: String,
    private val dinesykmeldteUrl: String,
    private val narmesteLederService: NarmesteLederService,
    private val pdlClient: PdlClient,
) {
    companion object {
        private const val WEEKS_BEFORE_DELETE = 4L
        private val log = LoggerFactory.getLogger(OppfolgingsplanVarselService::class.qualifiedName)
        private val objectMapper = createObjectMapper()
    }

    suspend fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        val eksternVarsling = accessControlService.canUserBeNotifiedByEmailOrSMS(varselHendelse.arbeidstakerFnr)
        varsleArbeidstakerViaBrukernotifikasjoner(
            varselHendelse = varselHendelse,
            eksternVarsling = eksternVarsling,
        )
    }

    suspend fun sendVarselTilNarmesteLeder(varselHendelse: NarmesteLederHendelse) {
        senderFacade.sendTilDineSykmeldte(
            varselHendelse,
            DineSykmeldteVarsel(
                ansattFnr = varselHendelse.arbeidstakerFnr,
                orgnr = varselHendelse.orgnummer,
                oppgavetype = varselHendelse.type.toDineSykmeldteHendelseType().toString(),
                lenke = null,
                tekst = DINE_SYKMELDTE_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST,
                utlopstidspunkt = OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
            ),
        )
        senderFacade.sendTilArbeidsgiverNotifikasjon(
            varselHendelse,
            ArbeidsgiverNotifikasjonNarmestelederInput(
                uuid = UUID.randomUUID(),
                virksomhetsnummer = varselHendelse.orgnummer,
                narmesteLederFnr = varselHendelse.narmesteLederFnr,
                ansattFnr = varselHendelse.arbeidstakerFnr,
                merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                messageText = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_MESSAGE_TEXT,
                epostTittel = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_EMAIL_TITLE,
                epostHtmlBody = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_EMAIL_BODY,
                hardDeleteDate = LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                meldingstype = Meldingstype.BESKJED,
                grupperingsid = UUID.randomUUID().toString(),
            ),
        )
    }

    suspend fun sendVarselbestillingTilNarmesteLeder(varselHendelse: NarmesteLederHendelse) {
        val varselbestilling = varselHendelse.requireOppfolgingsplanVarselbestillingData()
        val notifikasjonInnhold = varselbestilling.notifikasjonInnhold!!
        val varselTekst = requireNotNull(notifikasjonInnhold.varselTekst)

        if (varselbestilling.dineSykmeldteHendelseType != null) {
            senderFacade.sendTilDineSykmeldte(
                varselHendelse,
                DineSykmeldteVarsel(
                    ansattFnr = varselHendelse.arbeidstakerFnr,
                    orgnr = varselHendelse.orgnummer,
                    oppgavetype = varselbestilling.dineSykmeldteHendelseType,
                    lenke = null
                    tekst = varselTekst,
                    utlopstidspunkt = OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                ),
            )
        }
        if (varselbestilling.arbeidsgiverMeldingType != null) {
            senderFacade.sendTilArbeidsgiverNotifikasjon(
                varselHendelse = varselHendelse,
                notifikasjon =
                    ArbeidsgiverNotifikasjonNarmestelederInput(
                        uuid = UUID.randomUUID(),
                        virksomhetsnummer = varselHendelse.orgnummer,
                        narmesteLederFnr = varselHendelse.narmesteLederFnr,
                        ansattFnr = varselHendelse.arbeidstakerFnr,
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                        messageText = varselTekst,
                        epostTittel = notifikasjonInnhold.epostTittel,
                        epostHtmlBody = notifikasjonInnhold.epostBody,
                        hardDeleteDate = LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                        meldingstype = varselbestilling.requireMeldingstype(),
                        grupperingsid = UUID.randomUUID().toString(),
                    ),
                hendelseJson = objectMapper.writeValueAsString(varselHendelse),
            )
        }
    }

    suspend fun resendVarselbestillingTilArbeidsgiverNotifikasjon(varselFeilet: PUtsendtVarselFeilet): ArbeidsgiverVarselResendResult {
        val varselHendelse =
            runCatching { varselFeilet.toNarmesteLederHendelse() }
                .getOrElse {
                    log.error("Kunne ikke deserialisere feilet oppfølgingsplanvarsel for retry: {}", it.message)
                    return ArbeidsgiverVarselResendResult.PERMANENT_FAILURE
                }
        val varselbestilling =
            runCatching { varselHendelse.requireOppfolgingsplanVarselbestillingData() }
                .getOrElse {
                    log.error("Kunne ikke validere feilet oppfølgingsplanvarsel for retry: {}", it.message)
                    return ArbeidsgiverVarselResendResult.PERMANENT_FAILURE
                }
        val eksternReferanseUuid =
            runCatching {
                UUID.fromString(
                    varselFeilet.uuidEksternReferanse
                        ?: throw IllegalArgumentException("Mangler uuidEksternReferanse for feilet oppfølgingsplanvarsel"),
                )
            }.getOrElse {
                log.error("Kunne ikke parse uuidEksternReferanse for feilet oppfølgingsplanvarsel: {}", it.message)
                return ArbeidsgiverVarselResendResult.PERMANENT_FAILURE
            }
        val notifikasjonInnhold = varselbestilling.notifikasjonInnhold!!
        val varselTekst = requireNotNull(notifikasjonInnhold.varselTekst)
        return senderFacade.sendTilArbeidsgiverNotifikasjon(
            varselFeilet = varselFeilet,
            notifikasjon =
                ArbeidsgiverNotifikasjonNarmestelederInput(
                    uuid = eksternReferanseUuid,
                    virksomhetsnummer = varselHendelse.orgnummer,
                    narmesteLederFnr = varselHendelse.narmesteLederFnr,
                    ansattFnr = varselHendelse.arbeidstakerFnr,
                    merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                    messageText = varselTekst,
                    epostTittel = notifikasjonInnhold.epostTittel,
                    epostHtmlBody = notifikasjonInnhold.epostBody,
                    hardDeleteDate = LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                    meldingstype = varselbestilling.requireMeldingstype(),
                    grupperingsid = UUID.randomUUID().toString(),
                ),
        )
    }

    suspend fun sendOppfolgingsplanForesporselVarselTilNarmesteLeder(varselHendelse: NarmesteLederHendelse) {
        log.info("Send oppfolgingsplan foresporsel-varsel til narmeste leder")
        val narmesteLederRelasjon =
            narmesteLederService.getNarmesteLederRelasjon(
                varselHendelse.arbeidstakerFnr,
                varselHendelse.orgnummer,
            )

        if (narmesteLederRelasjon?.narmesteLederId == null) {
            log.error("Sender ikke varsel: narmesteLederRelasjon er null, eller mangler narmesteLederId")
            return
        }

        if (narmesteLederRelasjon.narmesteLederEpost == null) {
            log.error("Sender ikke varsel: narmesteLederRelasjon mangler epost")
            return
        }

        val url = "$dinesykmeldteUrl/${narmesteLederRelasjon.narmesteLederId}"
        val personData = pdlClient.hentPerson(personIdent = varselHendelse.arbeidstakerFnr)
        val grupperingsid = UUID.randomUUID().toString()

        val sakInput =
            NySakNarmesteLederInput(
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
            ArbeidsgiverNotifikasjonNarmestelederInput(
                uuid = UUID.randomUUID(),
                virksomhetsnummer = varselHendelse.orgnummer,
                narmesteLederFnr = varselHendelse.narmesteLederFnr,
                ansattFnr = varselHendelse.arbeidstakerFnr,
                merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                messageText = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_FORESPORSEL_MESSAGE_TEXT,
                epostTittel = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_FORESPORSEL_EMAIL_TITLE,
                epostHtmlBody = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGINGSPLAN_FORESPORSEL_EMAIL_BODY,
                hardDeleteDate = LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                meldingstype = Meldingstype.BESKJED,
                grupperingsid = grupperingsid,
                link = url,
            ),
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
            varseltype = BESKJED,
        )
    }

    fun getMessageText(arbeidstakerHendelse: ArbeidstakerHendelse): String? =
        when (arbeidstakerHendelse.type) {
            SM_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> BRUKERNOTIFIKASJON_OPPFOLGINGSPLAN_GODKJENNING_MESSAGE_TEXT
            else -> {
                log.error(
                    "Klarte ikke mappe varsel av type ${arbeidstakerHendelse.type} ved mapping av hendelsetype " +
                        "til oppfolgingsplanVarsel melding tekst",
                )
                null
            }
        }

    private fun NarmesteLederHendelse.requireOppfolgingsplanVarselbestillingData(): OppfolgingsplanVarselbestillingData {
        val payloadDataNode =
            data?.let {
                if (it is JsonNode) {
                    it
                } else {
                    objectMapper.valueToTree(it)
                }
            } ?: throw IllegalArgumentException("Oppfølgingsplanvarsel mangler feltet: data")

        val payload =
            runCatching { payloadDataNode.toOppfolgingsplanVarselbestillingData() }
                .getOrElse { throw IllegalArgumentException("Oppfølgingsplanvarsel har ugyldig format i data-feltet") }

        if (payload.notifikasjonInnhold == null) {
            throw IllegalArgumentException("Oppfølgingsplanvarsel mangler feltet: data.notifikasjonInnhold")
        }
        if (!payloadDataNode.path("notifikasjonInnhold").hasNonNull("varselTekst")) {
            throw IllegalArgumentException("Oppfølgingsplanvarsel mangler feltet: data.notifikasjonInnhold.varselTekst")
        }

        return payload
    }

    private fun OppfolgingsplanVarselbestillingData.requireMeldingstype(): Meldingstype =
        when (arbeidsgiverMeldingType) {
            Meldingstype.BESKJED.name -> Meldingstype.BESKJED
            Meldingstype.OPPGAVE.name -> Meldingstype.OPPGAVE
            null -> throw IllegalArgumentException("Oppfølgingsplanvarsel mangler feltet: data.varselType")
            else -> throw IllegalArgumentException("Oppfølgingsplanvarsel har ugyldig data.varselType=$arbeidsgiverMeldingType")
        }
}
