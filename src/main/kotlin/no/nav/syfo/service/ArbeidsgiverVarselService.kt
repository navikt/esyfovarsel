package no.nav.syfo.service

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import no.nav.syfo.ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.countArbeidsgivernotifikasjonerSakerByType
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.PSakInput
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.db.domain.toArbeidsgiverNotifikasjonTilAltinnRessursHendelse
import no.nav.syfo.db.getPaagaaendeArbeidsgivernotifikasjonerSakByType
import no.nav.syfo.db.isUtsendtVarselStored
import no.nav.syfo.db.storeArbeidsgivernotifikasjonerSak
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.db.storeUtsendtVarselFeilet
import no.nav.syfo.db.updateArbeidsgivernotifikasjonerSakStatusAndHardDeleteDate
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverNotifikasjonTilAltinnRessursHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselDataNotifikasjonInnhold
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NySakAltinnInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyStatusSakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.SAK_TYPE_DIALOGMOTE_UTEN_LEDER
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.SakStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val MONTHS_BEFORE_DELETE = 4L
private const val SAK_TITTEL_DIALOGMOTE = "Dialogmøte"
private val arbeidsgiverVarselObjectMapper = createObjectMapper()

class ArbeidsgiverVarselService(
    private val database: DatabaseInterface,
    private val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
) {
    private val log: Logger = LoggerFactory.getLogger(ArbeidsgiverVarselService::class.qualifiedName)
    private val objectMapper = arbeidsgiverVarselObjectMapper

    suspend fun sendVarselTilArbeidsgiver(hendelse: ArbeidsgiverNotifikasjonTilAltinnRessursHendelse) {
        sendVarselTilArbeidsgiver(
            hendelse = hendelse,
            lagreFeiletUtsending = true,
        )
    }

    suspend fun resendVarselTilArbeidsgiver(varselFeilet: PUtsendtVarselFeilet): ArbeidsgiverVarselResendResult {
        val hendelse =
            try {
                varselFeilet.toResendArbeidsgiverHendelse()
            } catch (exception: ArbeidsgiverVarselPermanentException) {
                log.error(
                    "Kunne ikke deserialisere feilet arbeidsgivervarsel for retry: type={}, kanal={}, feil={}",
                    varselFeilet.hendelsetypeNavn,
                    varselFeilet.kanal,
                    exception.message,
                )
                return ArbeidsgiverVarselResendResult.PERMANENT_FAILURE
            }

        return sendVarselTilArbeidsgiver(
            hendelse = hendelse,
            lagreFeiletUtsending = false,
        )
    }

    private suspend fun sendVarselTilArbeidsgiver(
        hendelse: ArbeidsgiverNotifikasjonTilAltinnRessursHendelse,
        lagreFeiletUtsending: Boolean,
    ): ArbeidsgiverVarselResendResult {
        val eksternReferanseUuid =
            hendelse.parseEksternReferanseUuid(lagreFeiletUtsending)
                ?: return ArbeidsgiverVarselResendResult.PERMANENT_FAILURE
        if (hendelse.isAlreadyStoredAsSent()) return ArbeidsgiverVarselResendResult.RESENT

        return try {
            sendOgLagreArbeidsgiverVarsel(
                hendelse = hendelse,
                eksternReferanseUuid = eksternReferanseUuid,
            )
            ArbeidsgiverVarselResendResult.RESENT
        } catch (exception: Exception) {
            handleArbeidsgiverVarselFailure(
                hendelse = hendelse,
                lagreFeiletUtsending = lagreFeiletUtsending,
                exception = exception,
            )
        }
    }

    private suspend fun sendOgLagreArbeidsgiverVarsel(
        hendelse: ArbeidsgiverNotifikasjonTilAltinnRessursHendelse,
        eksternReferanseUuid: UUID,
    ) {
        val notifikasjonInnhold = hendelse.requireNotifikasjonInnhold()
        val hardDeleteDate = createHardDeleteDate()
        val sak = getOrCreateSak(hendelse, hardDeleteDate)

        arbeidsgiverNotifikasjonService
            .sendNotifikasjon(
                hendelse.toArbeidsgiverNotifikasjonAltinnRessursInput(
                    eksternReferanseUuid = eksternReferanseUuid,
                    notifikasjonInnhold = notifikasjonInnhold,
                    sak = sak,
                    hardDeleteDate = hardDeleteDate,
                ),
            )
            ?: throw ArbeidsgiverVarselRetryableException(
                "ArbeidsgiverNotifikasjonService returnerte null ID ved utsending av arbeidsgivervarsel",
            )

        database.storeUtsendtVarsel(hendelse.toPUtsendtVarsel())
    }

    private suspend fun getOrCreateSak(
        hendelse: ArbeidsgiverNotifikasjonTilAltinnRessursHendelse,
        hardDeleteDate: LocalDateTime,
    ): ArbeidsgiverNotifikasjonSak {
        val existingSak =
            database.getPaagaaendeArbeidsgivernotifikasjonerSakByType(
                ansattFnr = hendelse.arbeidstakerFnr,
                virksomhetsnummer = hendelse.orgnummer,
                type = SAK_TYPE_DIALOGMOTE_UTEN_LEDER,
            )
        if (existingSak != null) {
            updateExistingSakHardDeleteDate(
                existingSak = existingSak,
                hardDeleteDate = hardDeleteDate,
            )
            return ArbeidsgiverNotifikasjonSak(
                id = existingSak.id,
                grupperingsid = existingSak.grupperingsid,
            )
        }

        val sakInput = hendelse.toNySakAltinnInput(SAK_TYPE_DIALOGMOTE_UTEN_LEDER, hardDeleteDate)
        val eksternSakId =
            arbeidsgiverNotifikasjonService.createNewSak(sakInput.toNySakMutation())
                ?: throw ArbeidsgiverVarselRetryableException(
                    "ArbeidsgiverNotifikasjonService returnerte null ID ved opprettelse av sak",
                )
        val sakId =
            database.storeArbeidsgivernotifikasjonerSak(
                sakInput = sakInput,
                eksternSakId = eksternSakId,
            )

        return ArbeidsgiverNotifikasjonSak(
            id = sakId,
            sakInput.grupperingsid,
        )
    }

    private suspend fun updateExistingSakHardDeleteDate(
        existingSak: PSakInput,
        hardDeleteDate: LocalDateTime,
    ) {
        val sakStatus = SakStatus.valueOf(existingSak.initiellStatus.name)

        arbeidsgiverNotifikasjonService.nyStatusSak(
            NyStatusSakInput(
                grupperingsId = existingSak.grupperingsid,
                merkelapp = existingSak.merkelapp,
                sakStatus = sakStatus,
                oppdatertHardDeleteDateTime = hardDeleteDate,
            ),
        ) ?: throw ArbeidsgiverVarselRetryableException(
            "ArbeidsgiverNotifikasjonService returnerte null ID ved oppdatering av hardDeleteDate på sak",
        )

        database.updateArbeidsgivernotifikasjonerSakStatusAndHardDeleteDate(
            sakId = existingSak.id,
            sakStatus = sakStatus,
            hardDeleteDate = hardDeleteDate,
        )
    }

    private fun generateGrupperingsid(
        arbeidstakerFnr: String,
        virksomhetsnummer: String,
        saktype: String,
    ): String {
        val existingSaker =
            database.countArbeidsgivernotifikasjonerSakerByType(
                ansattFnr = arbeidstakerFnr,
                virksomhetsnummer = virksomhetsnummer,
                type = saktype,
            )
        val seed =
            buildString {
                append(virksomhetsnummer)
                append('|')
                append(saktype)
                append('|')
                append(if (existingSaker == 0) "1" else existingSaker + 1)
                append('|')
                append(arbeidstakerFnr)
            }
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private fun lagreIkkeUtsendtArbeidsgiverVarsel(
        hendelse: ArbeidsgiverNotifikasjonTilAltinnRessursHendelse,
        exception: Throwable,
    ) {
        database.storeUtsendtVarselFeilet(
            PUtsendtVarselFeilet(
                uuid = UUID.randomUUID().toString(),
                uuidEksternReferanse = hendelse.eksternReferanseId,
                arbeidstakerFnr = hendelse.arbeidstakerFnr,
                narmesteLederFnr = null,
                orgnummer = hendelse.orgnummer,
                hendelsetypeNavn = hendelse.type.name,
                arbeidsgivernotifikasjonMerkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
                brukernotifikasjonerMeldingType = null,
                journalpostId = null,
                kanal = Kanal.ARBEIDSGIVERNOTIFIKASJON.name,
                feilmelding = exception.toSanitizedArbeidsgiverFeilmelding(),
                utsendtForsokTidspunkt = LocalDateTime.now(),
                isForcedLetter = false,
                isResendt = false,
                resendExhausted = false,
                hendelseJson = hendelse.serializeSafely(),
            ),
        )
    }

    private fun ArbeidsgiverNotifikasjonTilAltinnRessursHendelse.parseEksternReferanseUuid(lagreFeiletUtsending: Boolean): UUID? =
        runCatching { UUID.fromString(eksternReferanseId) }
            .getOrElse {
                val exception =
                    ArbeidsgiverVarselPermanentException("ArbeidsgiverHendelse har ugyldig eksternReferanseId-format")
                if (lagreFeiletUtsending) {
                    lagreIkkeUtsendtArbeidsgiverVarsel(this, exception)
                }
                log.warn(
                    "Avviser arbeidsgivervarsel med ugyldig eksternReferanseId: type={}, orgnummer={}, ressursId={}, kilde={}",
                    type,
                    orgnummer,
                    ressursId,
                    kilde,
                )
                null
            }

    private fun ArbeidsgiverNotifikasjonTilAltinnRessursHendelse.isAlreadyStoredAsSent(): Boolean {
        val isStored = database.isUtsendtVarselStored(eksternReferanseId, Kanal.ARBEIDSGIVERNOTIFIKASJON.name)
        if (isStored) {
            log.info(
                "Arbeidsgivervarsel er allerede lagret som sendt: type={}, orgnummer={}, ressursId={}, kilde={}",
                type,
                orgnummer,
                ressursId,
                kilde,
            )
        }
        return isStored
    }

    private fun ArbeidsgiverNotifikasjonTilAltinnRessursHendelse.toNySakAltinnInput(
        sakType: String,
        hardDeleteDate: LocalDateTime,
    ) = NySakAltinnInput(
        grupperingsid = generateGrupperingsid(arbeidstakerFnr, orgnummer, sakType),
        merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
        virksomhetsnummer = orgnummer,
        ansattFnr = arbeidstakerFnr,
        tittel = SAK_TITTEL_DIALOGMOTE,
        initiellStatus = SakStatus.MOTTATT,
        hardDeleteDate = hardDeleteDate,
        ressursId = ressursId,
        ressursUrl = ressursUrl,
    )

    private fun ArbeidsgiverNotifikasjonTilAltinnRessursHendelse.requireNotifikasjonInnhold(): VarselDataNotifikasjonInnhold =
        parseVarselData().notifikasjonInnhold
            ?: throw ArbeidsgiverVarselPermanentException(
                "ArbeidsgiverHendelse mangler feltet: data.notifikasjonInnhold",
            )

    private fun createHardDeleteDate(): LocalDateTime =
        LocalDate
            .now()
            .atStartOfDay()
            .plusDays(1)
            .plusMonths(MONTHS_BEFORE_DELETE)

    private fun ArbeidsgiverNotifikasjonTilAltinnRessursHendelse.toArbeidsgiverNotifikasjonAltinnRessursInput(
        eksternReferanseUuid: UUID,
        notifikasjonInnhold: VarselDataNotifikasjonInnhold,
        sak: ArbeidsgiverNotifikasjonSak,
        hardDeleteDate: LocalDateTime,
    ) = ArbeidsgiverNotifikasjonAltinnRessursInput(
        uuid = eksternReferanseUuid,
        virksomhetsnummer = orgnummer,
        merkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
        messageText = notifikasjonInnhold.smsTekst,
        epostTittel = notifikasjonInnhold.epostTittel,
        epostHtmlBody = notifikasjonInnhold.epostBody,
        hardDeleteDate = hardDeleteDate,
        grupperingsid = sak.grupperingsid,
        link = ressursUrl,
        ressursId = ressursId,
        ressursUrl = ressursUrl,
    )

    private fun ArbeidsgiverNotifikasjonTilAltinnRessursHendelse.toPUtsendtVarsel() =
        PUtsendtVarsel(
            uuid = UUID.randomUUID().toString(),
            fnr = arbeidstakerFnr,
            aktorId = null,
            narmesteLederFnr = null,
            orgnummer = orgnummer,
            type = type.name,
            kanal = Kanal.ARBEIDSGIVERNOTIFIKASJON.name,
            utsendtTidspunkt = LocalDateTime.now(),
            planlagtVarselId = null,
            eksternReferanse = eksternReferanseId,
            ferdigstiltTidspunkt = null,
            arbeidsgivernotifikasjonMerkelapp = ARBEIDSGIVERNOTIFIKASJON_DIALOGMOTE_MERKELAPP,
            isForcedLetter = false,
            journalpostId = null,
        )

    private fun handleArbeidsgiverVarselFailure(
        hendelse: ArbeidsgiverNotifikasjonTilAltinnRessursHendelse,
        lagreFeiletUtsending: Boolean,
        exception: Exception,
    ): ArbeidsgiverVarselResendResult {
        if (lagreFeiletUtsending) {
            lagreIkkeUtsendtArbeidsgiverVarsel(hendelse, exception)
        }
        logArbeidsgiverVarselFailure(hendelse, exception)
        return exception.toArbeidsgiverVarselResendResult()
    }

    private fun logArbeidsgiverVarselFailure(
        hendelse: ArbeidsgiverNotifikasjonTilAltinnRessursHendelse,
        exception: Exception,
    ) {
        val sanitizedFeilmelding = exception.toSanitizedArbeidsgiverFeilmelding()
        when (exception) {
            is ArbeidsgiverVarselPermanentException ->
                log.error(
                    "Permanent feil ved behandling av arbeidsgivervarsel: type={}, orgnummer={}, ressursId={}, kilde={}, feil={}",
                    hendelse.type,
                    hendelse.orgnummer,
                    hendelse.ressursId,
                    hendelse.kilde,
                    sanitizedFeilmelding,
                )

            else ->
                log.error(
                    "Feilet ved behandling av arbeidsgivervarsel: type={}, orgnummer={}, ressursId={}, kilde={}, feil={}",
                    hendelse.type,
                    hendelse.orgnummer,
                    hendelse.ressursId,
                    hendelse.kilde,
                    sanitizedFeilmelding,
                )
        }
    }

    private fun ArbeidsgiverNotifikasjonTilAltinnRessursHendelse.parseVarselData() =
        (data ?: throw ArbeidsgiverVarselPermanentException("ArbeidsgiverHendelse mangler feltet: data")).let { data ->
            runCatching {
                data.toVarselData()
            }.getOrElse { exception ->
                throw ArbeidsgiverVarselPermanentException(
                    "ArbeidsgiverHendelse har ugyldig format i feltet: ${exception.toVarselDataPath()}",
                )
            }
        }

    private fun ArbeidsgiverNotifikasjonTilAltinnRessursHendelse.serializeSafely(): String? =
        runCatching { objectMapper.writeValueAsString(this) }
            .onFailure { exception ->
                log.error(
                    "Kunne ikke serialisere arbeidsgiverhendelse for feillagring: type={}, orgnummer={}, ressursId={}, kilde={}, feil={}",
                    type,
                    orgnummer,
                    ressursId,
                    kilde,
                    exception.toSanitizedArbeidsgiverFeilmelding(),
                )
            }.getOrNull()
}

private data class ArbeidsgiverNotifikasjonSak(
    val id: String,
    val grupperingsid: String,
)

enum class ArbeidsgiverVarselResendResult {
    RESENT,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
}

private class ArbeidsgiverVarselRetryableException(
    override val message: String,
) : RuntimeException(message)

private class ArbeidsgiverVarselPermanentException(
    override val message: String,
) : RuntimeException(message)

private fun Throwable.toArbeidsgiverVarselResendResult(): ArbeidsgiverVarselResendResult =
    when (this) {
        is ArbeidsgiverVarselPermanentException -> ArbeidsgiverVarselResendResult.PERMANENT_FAILURE
        else -> ArbeidsgiverVarselResendResult.RETRYABLE_FAILURE
    }

private fun PUtsendtVarselFeilet.toResendArbeidsgiverHendelse(): ArbeidsgiverNotifikasjonTilAltinnRessursHendelse {
    val serializedHendelse =
        hendelseJson
            ?: throw ArbeidsgiverVarselPermanentException("Mangler hendelseJson for feilet arbeidsgiverhendelse")
    val rootNode =
        runCatching { arbeidsgiverVarselObjectMapper.readTree(serializedHendelse) }
            .getOrElse {
                throw ArbeidsgiverVarselPermanentException("Kunne ikke lese hendelseJson for feilet arbeidsgiverhendelse")
            }
    val dataNode = rootNode.requireDataNode()
    val hendelse =
        runCatching { toArbeidsgiverNotifikasjonTilAltinnRessursHendelse() }
            .getOrElse {
                throw ArbeidsgiverVarselPermanentException("Kunne ikke deserialisere feilet arbeidsgiverhendelse")
            }
    hendelse.data = dataNode
    return hendelse
}

private fun JsonNode.requireDataNode(): JsonNode =
    get("data")
        ?.takeUnless { it.isNull }
        ?: throw ArbeidsgiverVarselPermanentException("ArbeidsgiverHendelse mangler feltet: data")

private fun Throwable.toVarselDataPath(): String {
    val mappingPath =
        (this as? JsonMappingException)
            ?.path
            ?.let { path ->
                path.joinToString(".") { reference ->
                    reference.fieldName
                        ?: reference.index
                            .takeIf { it >= 0 }
                            ?.let { "[$it]" }
                            .orEmpty()
                }
            }
    if (mappingPath.isNullOrBlank()) {
        return "data"
    }
    return "data.$mappingPath"
}

private fun Throwable.toSanitizedArbeidsgiverFeilmelding(): String =
    when (this) {
        is ArbeidsgiverVarselRetryableException,
        is ArbeidsgiverVarselPermanentException,
        -> message

        is JsonMappingException -> "JsonMappingException i ${toVarselDataPath()}"
        else -> "Uventet feil (${this::class.simpleName ?: "UnknownException"})"
    } ?: "Uventet feil (UnknownException)"
