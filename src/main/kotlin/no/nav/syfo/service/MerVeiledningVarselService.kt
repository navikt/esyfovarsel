package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT
import no.nav.syfo.DITT_SYKEFRAVAER_MER_VEILEDNING_MESSAGE_TEXT
import no.nav.syfo.Environment
import no.nav.syfo.MER_VEILEDNING_URL
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import no.nav.syfo.metrics.tellMerVeiledningVarselSendt
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.OPPGAVE
import no.nav.syfo.utils.dataToVarselData
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

const val DITT_SYKEFRAVAER_HENDELSE_TYPE_MER_VEILEDNING = "ESYFOVARSEL_MER_VEILEDNING"
const val DAGER_TIL_DEAKTIVERING_AV_VARSEL: Long = 105

class MerVeiledningVarselService(
    val senderFacade: SenderFacade,
    val env: Environment,
    val accessControlService: AccessControlService,
) {

    suspend fun sendVarselTilArbeidstaker(
        arbeidstakerHendelse: ArbeidstakerHendelse,
    ) {
        val data = dataToVarselData(arbeidstakerHendelse.data)
        requireNotNull(data.journalpost)
        requireNotNull(data.journalpost.id)
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerHendelse.arbeidstakerFnr)

        if (userAccessStatus.canUserBeDigitallyNotified) {
            sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse)
        } else {
            senderFacade.sendBrevTilFysiskPrint(
                data.journalpost.uuid,
                arbeidstakerHendelse,
                data.journalpost.id,
                DistibusjonsType.VIKTIG,
            )
        }
        sendOppgaveTilDittSykefravaer(
            arbeidstakerHendelse.arbeidstakerFnr,
            UUID.randomUUID().toString(),
            arbeidstakerHendelse
        )
        tellMerVeiledningVarselSendt()
    }

    private fun sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse: ArbeidstakerHendelse) {
        val uuid = "${UUID.randomUUID()}"
        val fnr = arbeidstakerHendelse.arbeidstakerFnr
        val url = URI(env.urlEnv.baseUrlNavEkstern + MER_VEILEDNING_URL).toURL()
        senderFacade.sendTilBrukernotifikasjoner(
            uuid = uuid,
            mottakerFnr = fnr,
            content = BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT,
            url = url,
            arbeidstakerFnr = arbeidstakerHendelse.arbeidstakerFnr,
            orgnummer = arbeidstakerHendelse.orgnummer,
            hendelseType = arbeidstakerHendelse.type.name,
            varseltype = OPPGAVE,
            dagerTilDeaktivering = DAGER_TIL_DEAKTIVERING_AV_VARSEL,
        )
    }

    fun resendDigitaltVarselTilArbeidstaker(utsendtvarselFeilet: PUtsendtVarselFeilet): Boolean {
        val uuid = utsendtvarselFeilet.uuidEksternReferanse ?: UUID.randomUUID().toString()
        val fnr = utsendtvarselFeilet.arbeidstakerFnr
        val url = URI(env.urlEnv.baseUrlNavEkstern + MER_VEILEDNING_URL).toURL()
        return senderFacade.sendTilBrukernotifikasjoner(
            uuid = uuid,
            mottakerFnr = fnr,
            content = BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT,
            url = url,
            arbeidstakerFnr = fnr,
            orgnummer = utsendtvarselFeilet.orgnummer,
            hendelseType = utsendtvarselFeilet.hendelsetypeNavn,
            varseltype = OPPGAVE,
            dagerTilDeaktivering = DAGER_TIL_DEAKTIVERING_AV_VARSEL,
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
                DITT_SYKEFRAVAER_MER_VEILEDNING_MESSAGE_TEXT,
                MER_VEILEDNING_URL,
                Variant.INFO,
                true,
                DITT_SYKEFRAVAER_HENDELSE_TYPE_MER_VEILEDNING,
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
