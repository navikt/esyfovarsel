package no.nav.syfo.service

import no.nav.syfo.db.*
import no.nav.syfo.db.domain.toMikrofrontendSynlighet
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import no.nav.syfo.kafka.producers.mineside_microfrontend.*
import org.apache.commons.cli.MissingArgumentException
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class MikrofrontendService(
    val minSideMicrofrontendKafkaProducer: MinSideMicrofrontendKafkaProducer,
    val database: DatabaseInterface
) {
    private val actionEnabled = MinSideEvent.enable.toString()
    private val actionDisabled = MinSideEvent.disable.toString()
    private val dialogmoteMicrofrontendId = "syfo-dialog"
    private val log = LoggerFactory.getLogger(MikrofrontendService::class.java)

    fun enableDialogmoteFrontendForUser(hendelse: ArbeidstakerHendelse) {
        storeMikrofrontendSynlighetEntryInDb(hendelse)
        toggleDialogmoteFrontendForUser(
            hendelse.arbeidstakerFnr,
            actionEnabled
        )
    }

    fun disableDialogmoteFrontendForUser(hendelse: ArbeidstakerHendelse) {
        disableDialogmoteFrontendForUser(hendelse.arbeidstakerFnr)
    }

    fun disableDialogmoteFrontendForUser(fnr: String) {
        toggleDialogmoteFrontendForUser(
            fnr,
            actionDisabled
        )
        database.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste(fnr, Tjeneste.DIALOGMOTE)
    }

    fun updateDialogmoteFrontendForUser(hendelse: ArbeidstakerHendelse) {
        database.fetchMikrofrontendSynlighetEntriesByFnr(hendelse.arbeidstakerFnr)
            .lastOrNull { entry -> entry.tjeneste == Tjeneste.DIALOGMOTE.name }
            ?.let {
                database.updateMikrofrontendEntrySynligTom(
                    it.toMikrofrontendSynlighet(),
                    hendelse.motetidspunkt().toLocalDate()
                )
            }
            ?: run {
                log.warn(
                    "[MIKROFRONTEND_SERVICE]: Received ${hendelse.type} from VarselBus without corresponding entry" +
                        "in MIKROFRONTEND_SYNLIGHET DB-table. Creating new entry ..."
                )
                enableDialogmoteFrontendForUser(hendelse)
            }
    }

    // TODO: Rename?
    fun findAndCloseExpiredDialogmoteMikrofrontends() {
        database.fetchFnrsWithExpiredMicrofrontendEntries(Tjeneste.DIALOGMOTE).forEach { fnr ->
            disableDialogmoteFrontendForUser(fnr)
        }
    }

    private fun toggleDialogmoteFrontendForUser(
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
