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
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederRelasjon
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
        val notifikasjonInnhold = requireNotNull(varselbestilling.notifikasjonInnhold)
        val varselTekst = requireNotNull(notifikasjonInnhold.varselTekst)

        if (varselbestilling.dineSykmeldteHendelseType != null) {
            senderFacade.sendTilDineSykmeldte(
                varselHendelse,
                DineSykmeldteVarsel(
                    ansattFnr = varselHendelse.arbeidstakerFnr,
                    orgnr = varselHendelse.orgnummer,
                    oppgavetype = varselbestilling.dineSykmeldteHendelseType,
                    lenke = null,
                    tekst = varselTekst,
                    utlopstidspunkt = OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                ),
            )
        }
        if (varselbestilling.arbeidsgiverMeldingType != null) {
            val meldingstype = varselbestilling.requireMeldingstype()
            val hendelseJson = objectMapper.writeValueAsString(varselHendelse)
            val hardDeleteDate = LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)
            val eksternReferanseUuid = UUID.randomUUID()
            val arbeidsgiverSak =
                try {
                    getOrCreateOppfolgingsplanSak(
                        varselHendelse = varselHendelse,
                        hardDeleteDate = hardDeleteDate,
                    )
                } catch (exception: OppfolgingsplanVarselRetryableException) {
                    log.warn("Kunne ikke klargjøre arbeidsgiversak for oppfølgingsplanvarselbestilling: {}", exception.message)
                    senderFacade.lagreIkkeUtsendtArbeidsgiverNotifikasjonForNarmesteLeder(
                        varselHendelse = varselHendelse,
                        eksternReferanse = eksternReferanseUuid.toString(),
                        feilmelding = exception.message,
                        merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                        hendelseJson = hendelseJson,
                    )
                    return
                }
            senderFacade.sendTilArbeidsgiverNotifikasjonMedRetrylagring(
                varselHendelse = varselHendelse,
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
                        hardDeleteDate = hardDeleteDate,
                        meldingstype = meldingstype,
                        grupperingsid = arbeidsgiverSak.grupperingsid,
                        link = arbeidsgiverSak.link,
                    ),
                hendelseJson = hendelseJson,
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
        val notifikasjonInnhold = requireNotNull(varselbestilling.notifikasjonInnhold)
        val varselTekst = requireNotNull(notifikasjonInnhold.varselTekst)
        val meldingstype = varselbestilling.requireMeldingstype()
        val hardDeleteDate = LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)
        val arbeidsgiverSak =
            try {
                getOrCreateOppfolgingsplanSak(
                    varselHendelse = varselHendelse,
                    hardDeleteDate = hardDeleteDate,
                )
            } catch (exception: OppfolgingsplanVarselRetryableException) {
                log.warn("Kunne ikke klargjøre arbeidsgiversak for retry av oppfølgingsplanvarselbestilling: {}", exception.message)
                return ArbeidsgiverVarselResendResult.RETRYABLE_FAILURE
            }
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
                    hardDeleteDate = hardDeleteDate,
                    meldingstype = meldingstype,
                    grupperingsid = arbeidsgiverSak.grupperingsid,
                    link = arbeidsgiverSak.link,
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

        val hardDeleteDate = LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)
        val arbeidsgiverSak =
            getOrCreateOppfolgingsplanSak(
                varselHendelse = varselHendelse,
                hardDeleteDate = hardDeleteDate,
                narmesteLederRelasjon = narmesteLederRelasjon,
            )
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
                hardDeleteDate = hardDeleteDate,
                meldingstype = Meldingstype.BESKJED,
                grupperingsid = arbeidsgiverSak.grupperingsid,
                link = arbeidsgiverSak.link,
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

    private suspend fun getOrCreateOppfolgingsplanSak(
        varselHendelse: NarmesteLederHendelse,
        hardDeleteDate: LocalDateTime,
        narmesteLederRelasjon: NarmesteLederRelasjon? = null,
    ): OppfolgingsplanArbeidsgiverSak {
        val resolvedNarmesteLederRelasjon =
            narmesteLederRelasjon
                ?: try {
                    narmesteLederService.getNarmesteLederRelasjon(
                        varselHendelse.arbeidstakerFnr,
                        varselHendelse.orgnummer,
                    )
                } catch (exception: RuntimeException) {
                    throw OppfolgingsplanVarselRetryableException(
                        "Kunne ikke hente nærmeste-lederrelasjon for oppfølgingsplansak",
                        exception,
                    )
                }
        val narmesteLederId =
            resolvedNarmesteLederRelasjon?.narmesteLederId
                ?: throw OppfolgingsplanVarselRetryableException(
                    "Mangler nærmeste-lederrelasjon med narmesteLederId for oppfølgingsplansak",
                )
        val link = getDineSykmeldteNarmesteLederLink(narmesteLederId)
        val eksisterendeSak =
            try {
                senderFacade.getPaagaaendeSak(
                    narmesteLederId = narmesteLederId,
                    merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                )
            } catch (exception: RuntimeException) {
                throw OppfolgingsplanVarselRetryableException(
                    "Kunne ikke hente pågående oppfølgingsplansak",
                    exception,
                )
            }

        if (eksisterendeSak != null) {
            bumpHardDeleteDateForEksisterendeSak(
                eksisterendeSak = eksisterendeSak,
                hardDeleteDate = hardDeleteDate,
            )
            return OppfolgingsplanArbeidsgiverSak(
                grupperingsid = eksisterendeSak.grupperingsid,
                link = eksisterendeSak.lenke ?: link,
            )
        }

        val personData =
            try {
                pdlClient.hentPerson(personIdent = varselHendelse.arbeidstakerFnr)
            } catch (exception: RuntimeException) {
                throw OppfolgingsplanVarselRetryableException(
                    "Kunne ikke hente persondata for oppfølgingsplansak",
                    exception,
                )
            }
        val sakInput =
            NySakNarmesteLederInput(
                grupperingsid = UUID.randomUUID().toString(),
                narmestelederId = narmesteLederId,
                merkelapp = ARBEIDSGIVERNOTIFIKASJON_OPPFOLGING_MERKELAPP,
                virksomhetsnummer = varselHendelse.orgnummer,
                narmesteLederFnr = varselHendelse.narmesteLederFnr,
                ansattFnr = varselHendelse.arbeidstakerFnr,
                tittel = personData?.fullName()?.let { "Oppfølging av $it" } ?: "Oppfølging av sykmeldt",
                lenke = link,
                initiellStatus = SakStatus.MOTTATT,
                hardDeleteDate = hardDeleteDate,
            )

        try {
            senderFacade.createNewSak(sakInput)
        } catch (exception: RuntimeException) {
            throw OppfolgingsplanVarselRetryableException(
                "Kunne ikke opprette oppfølgingsplansak",
                exception,
            )
        }

        return OppfolgingsplanArbeidsgiverSak(
            grupperingsid = sakInput.grupperingsid,
            link = sakInput.lenke,
        )
    }

    private suspend fun bumpHardDeleteDateForEksisterendeSak(
        eksisterendeSak: no.nav.syfo.db.domain.PSakInput,
        hardDeleteDate: LocalDateTime,
    ) {
        val sakStatus =
            try {
                SakStatus.valueOf(eksisterendeSak.initiellStatus.name)
            } catch (exception: IllegalArgumentException) {
                throw OppfolgingsplanVarselRetryableException(
                    "Kunne ikke tolke saksstatus for eksisterende oppfølgingsplansak",
                    exception,
                )
            }

        try {
            senderFacade.nyStatusSak(
                sakId = eksisterendeSak.id,
                nyStatusSakInput =
                    no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyStatusSakInput(
                        grupperingsId = eksisterendeSak.grupperingsid,
                        merkelapp = eksisterendeSak.merkelapp,
                        sakStatus = sakStatus,
                        oppdatertHardDeleteDateTime = hardDeleteDate,
                    ),
            )
        } catch (exception: RuntimeException) {
            throw OppfolgingsplanVarselRetryableException(
                "Kunne ikke oppdatere hardDeleteDate for eksisterende oppfølgingsplansak",
                exception,
            )
        }
    }

    private fun getDineSykmeldteNarmesteLederLink(narmesteLederId: String): String = "$dinesykmeldteUrl/$narmesteLederId"

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

    private data class OppfolgingsplanArbeidsgiverSak(
        val grupperingsid: String,
        val link: String,
    )

    private class OppfolgingsplanVarselRetryableException(
        override val message: String,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause)
}
