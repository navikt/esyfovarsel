package no.nav.syfo.arbeidstakervarsel.service

import no.nav.syfo.arbeidstakervarsel.dao.ArbeidstakerKanal
import no.nav.syfo.arbeidstakervarsel.domain.ArbeidstakerVarselSendResult
import no.nav.syfo.arbeidstakervarsel.domain.DokumentdistribusjonVarsel
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import org.slf4j.LoggerFactory

class DokumentDistribusjonService(
    private val journalpostdistribusjonConsumer: JournalpostdistribusjonConsumer,
) {
    private val log = LoggerFactory.getLogger(DokumentDistribusjonService::class.java)

    suspend fun distribuerJournalpost(
        dokumentDistribusjonVarsel: DokumentdistribusjonVarsel
    ): ArbeidstakerVarselSendResult {
        val uuid = dokumentDistribusjonVarsel.uuid
        return try {
            val response = journalpostdistribusjonConsumer.distribuerJournalpost(
                journalpostId = dokumentDistribusjonVarsel.journalpostId,
                uuid = uuid,
                distribusjonstype = dokumentDistribusjonVarsel.distribusjonsType,
                tvingSentralPrint = dokumentDistribusjonVarsel.tvingSentralPrint
            )
            val bestillingsId = response.bestillingsId
            log.info(
                "Distribuerte journalpost: uuid={}, journalpostId={}, bestillingsId={}",
                uuid,
                dokumentDistribusjonVarsel.journalpostId,
                bestillingsId
            )
            ArbeidstakerVarselSendResult(
                success = true,
                uuid = uuid,
                kanal = ArbeidstakerKanal.DOKUMENTDISTRIBUSJON,
                exception = null
            )
        } catch (e: Exception) {
            log.error(
                "Feil ved distribusjon av journalpost: uuid={}, journalpostId={}",
                uuid,
                dokumentDistribusjonVarsel.journalpostId,
                e
            )
            ArbeidstakerVarselSendResult(
                success = false,
                uuid = uuid,
                kanal = ArbeidstakerKanal.DOKUMENTDISTRIBUSJON,
                exception = e
            )
        }
    }
}
