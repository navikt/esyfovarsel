package no.nav.syfo.service

import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideMicrofrontendKafkaProducer
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideEvent
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideRecord

class MicroFrontendService(
    val minSideMicrofrontendKafkaProducer: MinSideMicrofrontendKafkaProducer
) {
    private val actionEnabled = MinSideEvent.enable.toString()
    private val actionDisabled = MinSideEvent.disable.toString()
    private val dialogmoteMicrofrontendId = "syfo-dialog"

    fun enableDialogmoteFrontendForFnr(fnr: String) {
        toggleDialogmoteFrontendForFnr(
            fnr,
            actionEnabled
        )
    }

    fun disableDialogmoteFrontendForFnr(fnr: String) {
        toggleDialogmoteFrontendForFnr(
            fnr,
            actionDisabled
        )
    }

    private fun toggleDialogmoteFrontendForFnr(
        fnr: String,
        action: String
    ) {
        val minSideEvent = MinSideRecord(
            eventType = action,
            fnr = fnr,
            microfrontendId = dialogmoteMicrofrontendId
        )
        minSideMicrofrontendKafkaProducer.sendRecordToMinSideTopic(minSideEvent)
    }
}
