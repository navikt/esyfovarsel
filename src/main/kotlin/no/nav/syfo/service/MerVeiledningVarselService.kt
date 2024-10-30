package no.nav.syfo.service

import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import no.nav.syfo.BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT
import no.nav.syfo.DITT_SYKEFRAVAER_MER_VEILEDNING_MESSAGE_TEXT
import no.nav.syfo.Environment
import no.nav.syfo.MER_VEILEDNING_URL
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.Kanal.BREV
import no.nav.syfo.db.domain.Kanal.BRUKERNOTIFIKASJON
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.fetchFNRUtsendtMerVeiledningVarsler
import no.nav.syfo.db.storeUtsendtMerVeiledningVarselBackup
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.OPPGAVE
import no.nav.syfo.utils.dataToVarselData

const val DITT_SYKEFRAVAER_HENDELSE_TYPE_MER_VEILEDNING = "ESYFOVARSEL_MER_VEILEDNING"

class MerVeiledningVarselService(
    val senderFacade: SenderFacade,
    val env: Environment,
    val accessControlService: AccessControlService,
    private val databaseAccess: DatabaseInterface,
) {

    suspend fun sendVarselTilArbeidstakerFromJob(
        arbeidstakerHendelse: ArbeidstakerHendelse,
    ) {
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerHendelse.arbeidstakerFnr)
        val isBrukerReservert = !userAccessStatus.canUserBeDigitallyNotified

        val kanal = if (isBrukerReservert) {
            BREV
        } else {
            BRUKERNOTIFIKASJON
        }

        databaseAccess.storeUtsendtMerVeiledningVarselBackup(
            PUtsendtVarsel(
                uuid = UUID.randomUUID().toString(),
                fnr = arbeidstakerHendelse.arbeidstakerFnr,
                aktorId = null,
                narmesteLederFnr = null,
                orgnummer = arbeidstakerHendelse.orgnummer,
                type = arbeidstakerHendelse.type.name,
                kanal = kanal.name,
                utsendtTidspunkt = LocalDateTime.now(),
                planlagtVarselId = null,
                eksternReferanse = "${UUID.randomUUID()}",
                ferdigstiltTidspunkt = null,
                arbeidsgivernotifikasjonMerkelapp = null,
            ),
        )
    }

    suspend fun sendVarselTilArbeidstaker(
        arbeidstakerHendelse: ArbeidstakerHendelse,
    ) {
        val data = dataToVarselData(arbeidstakerHendelse.data)
        requireNotNull(data.journalpost)
        requireNotNull(data.journalpost.id)
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerHendelse.arbeidstakerFnr)
        if (databaseAccess.fetchFNRUtsendtMerVeiledningVarsler().contains(arbeidstakerHendelse.arbeidstakerFnr)) {
            return
        }

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
    }

    private fun sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse: ArbeidstakerHendelse) {
        val uuid = "${UUID.randomUUID()}"
        val fnr = arbeidstakerHendelse.arbeidstakerFnr
        val url = URL(env.urlEnv.baseUrlNavEkstern + MER_VEILEDNING_URL)

        senderFacade.sendTilBrukernotifikasjoner(
            uuid = uuid,
            mottakerFnr = fnr,
            content = BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT,
            url = url,
            varselHendelse = arbeidstakerHendelse,
            varseltype = OPPGAVE
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
