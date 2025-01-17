package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJON_AKTIVITETSKRAV_FORHANDSVARSEL_STANS_SMS_TEXT
import no.nav.syfo.BRUKERNOTIFIKASJON_AKTIVITETSKRAV_FORHANDSVARSEL_STANS_TEXT
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.service.SenderFacade.InternalBrukernotifikasjonType.OPPGAVE
import no.nav.syfo.utils.dataToVarselData
import org.slf4j.LoggerFactory
import java.net.URL

class AktivitetspliktForhandsvarselVarselService(
    val senderFacade: SenderFacade,
    val accessControlService: AccessControlService,
    val urlAktivitetskravInfoPage: String,
    private val isSendingEnabled: Boolean,
) {
    suspend fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        if (isSendingEnabled) {
            log.info("[FORHAANDSVARSEL] sending enabled")
            val data = dataToVarselData(varselHendelse.data)
            requireNotNull(data.aktivitetskrav)
            if (!data.aktivitetskrav.sendForhandsvarsel) {
                return
            }
            requireNotNull(data.journalpost)
            requireNotNull(data.journalpost.id)

            val userAccessStatus = accessControlService.getUserAccessStatus(varselHendelse.arbeidstakerFnr)
            if (userAccessStatus.canUserBeDigitallyNotified) {
                senderFacade.sendTilBrukernotifikasjoner(
                    uuid = data.journalpost.uuid, // aktivitetskravUuid
                    mottakerFnr = varselHendelse.arbeidstakerFnr,
                    content = BRUKERNOTIFIKASJON_AKTIVITETSKRAV_FORHANDSVARSEL_STANS_TEXT,
                    url = URL(urlAktivitetskravInfoPage),
                    varselHendelse = varselHendelse,
                    varseltype = OPPGAVE,
                    eksternVarsling = true,
                    smsContent = BRUKERNOTIFIKASJON_AKTIVITETSKRAV_FORHANDSVARSEL_STANS_SMS_TEXT,
                    journalpostId = data.journalpost.id, // journalpostId
                )
            } else {
                log.info("Sending [FORHAANDSVARSEL] to print")
                senderFacade.sendBrevTilFysiskPrint(
                    data.journalpost.uuid, // aktivitetskravUuid
                    varselHendelse,
                    data.journalpost.id, // journalpostId
                    DistibusjonsType.VIKTIG,
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AktivitetspliktForhandsvarselVarselService::class.qualifiedName)
    }
}
