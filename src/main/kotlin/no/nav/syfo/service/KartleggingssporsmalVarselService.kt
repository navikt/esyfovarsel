package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJONER_SYKEFRAVAER_KARTLEGGINGSSPORSMAL_MESSAGE_TEXT
import no.nav.syfo.BRUKERNOTIFIKASJONER_SYKEFRAVAER_KARTLEGGINGSSPORSMAL_SMS_TEXT
import no.nav.syfo.Environment
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.metrics.countKartleggingssporsmalVarselSendt
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.OPPGAVE
import java.net.URI
import java.util.*

class KartleggingssporsmalVarselService(
    val senderFacade: SenderFacade,
    val env: Environment,
    val accessControlService: AccessControlService,
) {
    suspend fun sendKartleggingssporsmalTilArbeidstaker(
        arbeidstakerHendelse: ArbeidstakerHendelse,
    ) {
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerHendelse.arbeidstakerFnr)
        sendDigitaltVarselTilArbeidstaker(
            arbeidstakerHendelse = arbeidstakerHendelse,
            eksternVarsling = userAccessStatus.canUserBeDigitallyNotified
        )
        countKartleggingssporsmalVarselSendt()
    }

    private fun sendDigitaltVarselTilArbeidstaker(
        arbeidstakerHendelse: ArbeidstakerHendelse,
        eksternVarsling: Boolean
    ) {
        val fnr = arbeidstakerHendelse.arbeidstakerFnr
        val url = URI(env.urlEnv.baseUrlNavEkstern + KARTLEGGINGSSPORSMAL_URL).toURL()
        senderFacade.sendTilBrukernotifikasjoner(
            uuid = UUID.randomUUID().toString(),
            mottakerFnr = fnr,
            smsContent = BRUKERNOTIFIKASJONER_SYKEFRAVAER_KARTLEGGINGSSPORSMAL_SMS_TEXT,
            content = BRUKERNOTIFIKASJONER_SYKEFRAVAER_KARTLEGGINGSSPORSMAL_MESSAGE_TEXT,
            url = url,
            arbeidstakerFnr = arbeidstakerHendelse.arbeidstakerFnr,
            orgnummer = arbeidstakerHendelse.orgnummer,
            hendelseType = arbeidstakerHendelse.type.name,
            varseltype = OPPGAVE,
            dagerTilDeaktivering = DAGER_TIL_DEAKTIVERING_AV_VARSEL,
            eksternVarsling = eksternVarsling,
            storeFailedUtsending = eksternVarsling
        )
    }

    fun resendDigitaltVarselTilArbeidstaker(utsendtvarselFeilet: PUtsendtVarselFeilet): Boolean {
        val uuid = utsendtvarselFeilet.uuidEksternReferanse ?: UUID.randomUUID().toString()
        val fnr = utsendtvarselFeilet.arbeidstakerFnr
        val url = URI(env.urlEnv.baseUrlNavEkstern + KARTLEGGINGSSPORSMAL_URL).toURL()
        return senderFacade.sendTilBrukernotifikasjoner(
            uuid = uuid,
            mottakerFnr = fnr,
            smsContent = BRUKERNOTIFIKASJONER_SYKEFRAVAER_KARTLEGGINGSSPORSMAL_SMS_TEXT,
            content = BRUKERNOTIFIKASJONER_SYKEFRAVAER_KARTLEGGINGSSPORSMAL_MESSAGE_TEXT,
            url = url,
            arbeidstakerFnr = fnr,
            orgnummer = utsendtvarselFeilet.orgnummer,
            hendelseType = utsendtvarselFeilet.hendelsetypeNavn,
            varseltype = OPPGAVE,
            dagerTilDeaktivering = DAGER_TIL_DEAKTIVERING_AV_VARSEL,
            storeFailedUtsending = false,
        )
    }

    companion object {
        private const val DAGER_TIL_DEAKTIVERING_AV_VARSEL: Long = 30
        private const val KARTLEGGINGSSPORSMAL_URL = "/syk/kartleggingssporsmal"
    }
}
