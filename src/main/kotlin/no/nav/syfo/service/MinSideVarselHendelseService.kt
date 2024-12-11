package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import org.apache.kafka.clients.consumer.ConsumerRecord

class MinSideVarselHendelseService(database: DatabaseInterface) {
    fun processRecord(record: ConsumerRecord<String, String>) {
        // Todo: process hendelse

        // filter oppgave and AK plikt, oppgave
         /* store in DB:
      Update Utsendte varsler tabell, ferdigstilt tidspunkt --> DatabaseInterface.setUtsendtVarselToFerdigstilt(eksternRef: String)
        */
    }
}
