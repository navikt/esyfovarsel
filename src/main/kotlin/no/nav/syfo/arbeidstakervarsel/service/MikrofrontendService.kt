package no.nav.syfo.arbeidstakervarsel.service

import no.nav.syfo.arbeidstakervarsel.domain.MicrofrontendAction
import no.nav.syfo.arbeidstakervarsel.domain.MicrofrontendEvent
import no.nav.syfo.arbeidstakervarsel.domain.MicrofrontendType
import no.nav.syfo.arbeidstakervarsel.domain.toMicrofrontendId
import no.nav.syfo.arbeidstakervarsel.domain.toTjeneste
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.db.storeMikrofrontendSynlighetEntry
import no.nav.syfo.db.updateMikrofrontendEntrySynligTomByFnrAndTjeneste
import no.nav.syfo.kafka.producers.mineside_microfrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideEvent
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideMicrofrontendKafkaProducer
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideRecord

class MikrofrontendService(
    private val minSideMicrofrontendKafkaProducer: MinSideMicrofrontendKafkaProducer,
    private val database: DatabaseInterface
) {
    fun handleMicrofrontendEvent(mottakerFnr: String, event: MicrofrontendEvent) {
        when (event.action) {
            MicrofrontendAction.ENABLE -> enable(mottakerFnr, event)
            MicrofrontendAction.DISABLE -> disable(mottakerFnr, event)
        }
    }

    private fun enable(fnr: String, event: MicrofrontendEvent) {
        val tjeneste = event.microfrontendType.toTjeneste()
        if (!hasActiveMicrofrontend(fnr, event.microfrontendType)) {
            sendMinSideRecord(
                fnr = fnr,
                type = event.microfrontendType,
                event = MinSideEvent.ENABLE
            )
            database.storeMikrofrontendSynlighetEntry(
                MikrofrontendSynlighet(
                    synligFor = fnr,
                    tjeneste = tjeneste,
                    synligTom = event.synligTom
                )
            )
        } else {
            database.updateMikrofrontendEntrySynligTomByFnrAndTjeneste(
                fnr = fnr,
                tjeneste = tjeneste,
                newSynligTom = event.synligTom
            )
        }
    }

    private fun disable(fnr: String, event: MicrofrontendEvent) {
        sendMinSideRecord(
            fnr = fnr,
            type = event.microfrontendType,
            event = MinSideEvent.DISABLE
        )
        database.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste(
            fnr = fnr,
            tjeneste = event.microfrontendType.toTjeneste()
        )
    }

    private fun sendMinSideRecord(
        fnr: String,
        type: MicrofrontendType,
        event: MinSideEvent
    ) {
        minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(
            MinSideRecord(
                eventType = event.name,
                fnr = fnr,
                microfrontendId = type.toMicrofrontendId()
            )
        )
    }

    private fun hasActiveMicrofrontend(fnr: String, type: MicrofrontendType): Boolean =
        database.fetchMikrofrontendSynlighetEntriesByFnr(fnr)
            .any { it.tjeneste == type.name && it.synligTom != null }
}
