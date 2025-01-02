package no.nav.syfo.job

import java.time.LocalDateTime
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.fetchAlleUferdigstilteAktivitetspliktVarsler
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.service.SenderFacade
import org.slf4j.LoggerFactory

class SendForcedAktivitetspliktLetterJob(private val db: DatabaseInterface, private val senderFacade: SenderFacade) {
    private val log = LoggerFactory.getLogger(SendForcedAktivitetspliktLetterJob::class.java)

    suspend fun sendForcedLetterFromJob(): Int {
        val allUnreadVarsler = db.fetchAlleUferdigstilteAktivitetspliktVarsler()
        val unreadVarslerOverdude = allUnreadVarsler.filter { isVarselUnredIn2Days(it) }

        log.info("SendForcedAktivitetspliktLetterJob is about to send ${unreadVarslerOverdude.size} forced letters")
        var sentForcedLettersAmount = 0

        unreadVarslerOverdude.forEach { pUtsendtVarsel ->
            if (pUtsendtVarsel.journalpostId.isNullOrBlank()) {
                log.error("[FORCED PHYSICAL PRINT]: User can not be notified by letter due to missing journalpostId in varsel with uuid: ${pUtsendtVarsel.uuid}")
            } else {
                senderFacade.sendForcedBrevTilFysiskPrint(
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
                sentForcedLettersAmount++
            }
        }
        log.info("[FORCED PHYSICAL PRINT]: SendForcedAktivitetspliktLetterJob sent ${sentForcedLettersAmount} forced letters")
        return sentForcedLettersAmount
    }

    fun isVarselUnredIn2Days(pUtsendtVarsel: PUtsendtVarsel): Boolean {
        return pUtsendtVarsel.utsendtTidspunkt.isBefore(
            LocalDateTime.now().minusDays(2)
        )
                || pUtsendtVarsel.utsendtTidspunkt.isEqual(LocalDateTime.now().minusDays(2))
    }
}
