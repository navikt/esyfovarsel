package no.nav.syfo.job

import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.fetchAlleUferdigstilteAktivitetspliktVarsler
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.service.SenderFacade
import org.slf4j.LoggerFactory

class SendAktivitetspliktLetterToSentralPrintJob(private val db: DatabaseInterface, private val senderFacade: SenderFacade) {
    private val log = LoggerFactory.getLogger(SendAktivitetspliktLetterToSentralPrintJob::class.java)

    suspend fun sendLetterToTvingSentralPrintFromJob(): Int {
        val unreadVarslerOverdude = db.fetchAlleUferdigstilteAktivitetspliktVarsler()

        log.info(
            "SendAktivitetspliktLetterToSentralPrintJob is about to send ${unreadVarslerOverdude.size} forced letters"
        )
        var sentToTvingSentralPrintLettersAmount = 0

        unreadVarslerOverdude.forEach { pUtsendtVarsel ->
            if (pUtsendtVarsel.journalpostId.isNullOrBlank()) {
                log.error(
                    "[RENOTIFICATE VIA SENTRAL PRINT DIRECTLY]: User can not be notified by letter due to missing journalpostId in varsel with uuid: ${pUtsendtVarsel.uuid}"
                )
            } else {
                senderFacade.sendBrevTilTvingSentralPrint(
                    uuid = pUtsendtVarsel.uuid,
                    varselHendelse = ArbeidstakerHendelse(
                        type = HendelseType.SM_AKTIVITETSPLIKT,
                        ferdigstill = null,
                        data = null,
                        arbeidstakerFnr = pUtsendtVarsel.fnr,
                        orgnummer = pUtsendtVarsel.orgnummer,
                    ),
                    distribusjonsType = DistibusjonsType.VIKTIG,
                    journalpostId = pUtsendtVarsel.journalpostId
                )
                sentToTvingSentralPrintLettersAmount++
            }
        }
        log.info(
            "[RENOTIFICATE VIA SENTRAL PRINT DIRECTLY]: sendLetterToTvingSentralPrintFromJob sent $sentToTvingSentralPrintLettersAmount letters"
        )
        return sentToTvingSentralPrintLettersAmount
    }
}
