package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste
import no.nav.syfo.db.storeMikrofrontendSynlighetEntry
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import no.nav.syfo.kafka.producers.mineside_microfrontend.*
import org.apache.commons.cli.MissingArgumentException
import java.time.LocalDateTime

class MikrofrontendService(
    val minSideMicrofrontendKafkaProducer: MinSideMicrofrontendKafkaProducer,
    val database: DatabaseInterface
) {
    private val actionEnabled = MinSideEvent.enable.toString()
    private val actionDisabled = MinSideEvent.disable.toString()
    private val dialogmoteMicrofrontendId = "syfo-dialog"

    fun enableDialogmoteFrontendForFnr(hendelse: ArbeidstakerHendelse) {
        storeMikrofrontendSynlighetEntryInDb(hendelse)
        toggleDialogmoteFrontendForFnr(
            hendelse.arbeidstakerFnr,
            actionEnabled
        )
    }

    fun disableDialogmoteFrontendForFnr(hendelse: ArbeidstakerHendelse) {
        toggleDialogmoteFrontendForFnr(
            hendelse.arbeidstakerFnr,
            actionDisabled
        )
        database.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste(hendelse.arbeidstakerFnr, Tjeneste.DIALOGMOTE)
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

    private fun storeMikrofrontendSynlighetEntryInDb(hendelse: ArbeidstakerHendelse) {
        database.storeMikrofrontendSynlighetEntry(
            MikrofrontendSynlighet(
                synligFor = hendelse.arbeidstakerFnr,
                tjeneste = Tjeneste.DIALOGMOTE,
                synligTom = hendelse.motetidspunkt().toLocalDate()
            )
        )
    }

    private fun ArbeidstakerHendelse.motetidspunkt(): LocalDateTime {
        this.data?.let { data ->
            val varseldata = data.toVarselData()
            val varselMotetidspunkt = varseldata.motetidspunkt
            return varselMotetidspunkt?.tidspunkt
                ?: throw NullPointerException("'tidspunkt'-felt er null i VarselDataMotetidspunkt-objekt")
        } ?: throw MissingArgumentException("Mangler datafelt i ArbeidstakerHendelse til MicrofrontendService")
    }
}
