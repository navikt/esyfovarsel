package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJONER_KARTLEGGING_MESSAGE_TEXT
import no.nav.syfo.DITT_SYKEFRAVAER_KARTLEGGING_MESSAGE_TEXT
import no.nav.syfo.Environment
import no.nav.syfo.KARTLEGGING_URL
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import no.nav.syfo.metrics.tellKartleggingVarselSendt
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.OPPGAVE
import no.nav.syfo.utils.dataToVarselData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

const val DITT_SYKEFRAVAER_HENDELSE_TYPE_KARTLEGGING = "ESYFOVARSEL_KARTLEGGING"
const val DAGER_TIL_DEAKTIVERING: Long = 20 // todo input fra modia?

class KartleggingVarselService(
    val senderFacade: SenderFacade,
    val env: Environment,
    val accessControlService: AccessControlService,
) {

    private val log: Logger = LoggerFactory.getLogger(MerVeiledningVarselService::class.qualifiedName)

    suspend fun sendVarselTilArbeidstaker(
        arbeidstakerHendelse: ArbeidstakerHendelse,
    ) {
        val data = dataToVarselData(arbeidstakerHendelse.data)
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerHendelse.arbeidstakerFnr)

        if (userAccessStatus.canUserBeDigitallyNotified) {
            sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse)
        } else {
            log.warn(
                "Skip sending Kartlegging-varsel because user cannot be digitally notified. " +
                    "This should not happen because Modia checks reservation status"
            )
        }
        sendOppgaveTilDittSykefravaer(
            arbeidstakerHendelse.arbeidstakerFnr,
            UUID.randomUUID().toString(),
            arbeidstakerHendelse
        )
        tellKartleggingVarselSendt()
    }

    private fun sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse: ArbeidstakerHendelse) {
        val uuid = "${UUID.randomUUID()}"
        val fnr = arbeidstakerHendelse.arbeidstakerFnr
        val url = URI(env.urlEnv.baseUrlNavEkstern + KARTLEGGING_URL).toURL()
        senderFacade.sendTilBrukernotifikasjoner(
            uuid = uuid,
            mottakerFnr = fnr,
            content = BRUKERNOTIFIKASJONER_KARTLEGGING_MESSAGE_TEXT,
            url = url,
            arbeidstakerFnr = arbeidstakerHendelse.arbeidstakerFnr,
            orgnummer = arbeidstakerHendelse.orgnummer,
            hendelseType = arbeidstakerHendelse.type.name,
            varseltype = OPPGAVE,
            dagerTilDeaktivering = DAGER_TIL_DEAKTIVERING,
        )
    }

    fun resendDigitaltVarselTilArbeidstaker(utsendtvarselFeilet: PUtsendtVarselFeilet): Boolean {
        val uuid = utsendtvarselFeilet.uuidEksternReferanse ?: UUID.randomUUID().toString()
        val fnr = utsendtvarselFeilet.arbeidstakerFnr
        val url = URI(env.urlEnv.baseUrlNavEkstern + KARTLEGGING_URL).toURL()
        return senderFacade.sendTilBrukernotifikasjoner(
            uuid = uuid,
            mottakerFnr = fnr,
            content = BRUKERNOTIFIKASJONER_KARTLEGGING_MESSAGE_TEXT,
            url = url,
            arbeidstakerFnr = fnr,
            orgnummer = utsendtvarselFeilet.orgnummer,
            hendelseType = utsendtvarselFeilet.hendelsetypeNavn,
            varseltype = OPPGAVE,
            dagerTilDeaktivering = DAGER_TIL_DEAKTIVERING,
            storeFailedUtsending = false,
        )
    }

    private fun sendOppgaveTilDittSykefravaer(
        fnr: String,
        uuid: String,
        arbeidstakerHendelse: ArbeidstakerHendelse,
    ) {
        val melding = DittSykefravaerMelding(
            OpprettMelding(
                DITT_SYKEFRAVAER_KARTLEGGING_MESSAGE_TEXT,
                KARTLEGGING_URL,
                Variant.INFO,
                true,
                DITT_SYKEFRAVAER_HENDELSE_TYPE_KARTLEGGING,
                LocalDateTime.now().plusWeeks(13).toInstant(ZoneOffset.UTC),
            ),
            null,
            fnr,
        )
        senderFacade.sendTilDittSykefravaer(
            arbeidstakerHendelse,
            DittSykefravaerVarsel(
                uuid,
                melding,
            ),
        )
    }
}
