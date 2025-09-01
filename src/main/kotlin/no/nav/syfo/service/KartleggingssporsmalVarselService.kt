package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT
import no.nav.syfo.Environment
import no.nav.syfo.MER_VEILEDNING_URL
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import no.nav.syfo.metrics.countKartleggingssporsmalVarselSendt
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.OPPGAVE
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class KartleggingssporsmalVarselService(
    val senderFacade: SenderFacade,
    val env: Environment,
    val accessControlService: AccessControlService,
) {
    suspend fun sendKartleggingssporsmalTilArbeidstaker(
        arbeidstakerHendelse: ArbeidstakerHendelse,
    ) {
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerHendelse.arbeidstakerFnr)
        if (userAccessStatus.canUserBeDigitallyNotified) {
            sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse)
            sendOppgaveTilDittSykefravaer(
                arbeidstakerHendelse.arbeidstakerFnr,
                UUID.randomUUID().toString(),
                arbeidstakerHendelse
            )
            countKartleggingssporsmalVarselSendt()
        }
    }

    private fun sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse: ArbeidstakerHendelse) {
        val fnr = arbeidstakerHendelse.arbeidstakerFnr
        val url = URI(env.urlEnv.baseUrlNavEkstern + MER_VEILEDNING_URL).toURL()
        senderFacade.sendTilBrukernotifikasjoner(
            uuid = UUID.randomUUID().toString(),
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

    // TODO
    // Hvilken url bruker vi for `lenke`?
    // Hvor lang varighet skal vi har i `synligFremTil`?
    // Hva skal stå i `DITT_SYKEFRAVAER_KARTLEGGINGSSPORSMAL_MESSAGE_TEXT`?
    // Hvor ma vi legge til stotte for `ESYFOVARSEL_KARTLEGGINGSSPORSMAL`?
    // Hva blir riktig verdi for `KARTLEGGINGSSPORSMAL_URL`?
    private fun sendOppgaveTilDittSykefravaer(
        fnr: String,
        uuid: String,
        arbeidstakerHendelse: ArbeidstakerHendelse,
    ) {
        val melding = DittSykefravaerMelding(
            OpprettMelding(
                DITT_SYKEFRAVAER_KARTLEGGINGSSPORSMAL_MESSAGE_TEXT,
                "",
                Variant.INFO,
                true,
                DITT_SYKEFRAVAER_HENDELSE_TYPE_KARTLEGGINGSSPORSMAL,
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

    companion object {
        private const val DITT_SYKEFRAVAER_KARTLEGGINGSSPORSMAL_MESSAGE_TEXT = ""
        private const val DITT_SYKEFRAVAER_HENDELSE_TYPE_KARTLEGGINGSSPORSMAL =
            "ESYFOVARSEL_KARTLEGGINGSSPORSMAL"
        private const val KARTLEGGINGSSPORSMAL_URL = "/syk/meroppfolging/?"
    }
}
