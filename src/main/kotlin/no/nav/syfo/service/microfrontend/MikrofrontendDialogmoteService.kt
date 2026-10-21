package no.nav.syfo.service.microfrontend

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste
import no.nav.syfo.db.domain.toMikrofrontendSynlighet
import no.nav.syfo.db.fetchFnrsWithExpiredMicrofrontendEntries
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.db.updateMikrofrontendEntrySynligTomByExistingEntry
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.getSynligTom
import no.nav.syfo.kafka.producers.minesidemicrofrontend.MinSideRecord
import no.nav.syfo.kafka.producers.minesidemicrofrontend.Tjeneste
import no.nav.syfo.service.microfrontend.MikrofrontendService.Companion.actionDisabled
import no.nav.syfo.service.microfrontend.MikrofrontendService.Companion.actionEnabled
import org.slf4j.LoggerFactory

class MikrofrontendDialogmoteService(
    val database: DatabaseInterface,
) {
    private val log = LoggerFactory.getLogger(MikrofrontendDialogmoteService::class.qualifiedName)

    companion object {
        const val DIALOGMOTE_MIKROFRONTEND_ID = "syfo-dialog"
        const val MOTEBEHOV_MIKROFRONTEND_ID = "syfo-motebehov"
    }

    fun updateDialogmoteFrontendForUserByHendelse(hendelse: ArbeidstakerHendelse): MinSideRecord? =
        when (hendelse.type) {
            HendelseType.SM_DIALOGMOTE_NYTT_TID_STED -> setNewDateForMikrofrontendUser(hendelse)
            HendelseType.SM_DIALOGMOTE_AVLYST,
            HendelseType.SM_DIALOGMOTE_REFERAT,
            -> minSideRecordDisabled(hendelse.arbeidstakerFnr)
            HendelseType.SM_DIALOGMOTE_INNKALT -> {
                // Erstatter eventuelle tidligere rader, slik at bare synligTom for siste innkalling gjelder
                database.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste(hendelse.arbeidstakerFnr, Tjeneste.DIALOGMOTE)
                minSideRecordEnabled(hendelse.arbeidstakerFnr)
            }
            else -> null
        }

    fun updateMotebehovFrontendForUserByHendelse(hendelse: ArbeidstakerHendelse): MinSideRecord =
        if (hendelse.ferdigstill == true) {
            motebehovRecordDisabled(hendelse.arbeidstakerFnr)
        } else {
            database.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste(hendelse.arbeidstakerFnr, Tjeneste.MOTEBEHOV)
            motebehovRecordEnabled(hendelse.arbeidstakerFnr)
        }

    fun findExpiredDialogmoteMikrofrontends(): List<Triple<String, String, Tjeneste>> =
        database
            .fetchFnrsWithExpiredMicrofrontendEntries(Tjeneste.DIALOGMOTE)
            .map { Triple(it, DIALOGMOTE_MIKROFRONTEND_ID, Tjeneste.DIALOGMOTE) }

    private fun setNewDateForMikrofrontendUser(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        return database
            .fetchMikrofrontendSynlighetEntriesByFnr(hendelse.arbeidstakerFnr)
            .lastOrNull { entry -> entry.tjeneste == Tjeneste.DIALOGMOTE.name }
            ?.let {
                database.updateMikrofrontendEntrySynligTomByExistingEntry(
                    it.toMikrofrontendSynlighet(),
                    hendelse.getSynligTom()!!.toLocalDate(),
                )
                return null
            }
            ?: run {
                log.warn(
                    "[MIKROFRONTEND_SERVICE]: Received ${hendelse.type} from VarselBus without corresponding entry " +
                        "in MIKROFRONTEND_SYNLIGHET DB-table. Creating new entry ...",
                )
                minSideRecordEnabled(hendelse.arbeidstakerFnr)
            }
    }

    private fun minSideRecordEnabled(fnr: String) =
        MinSideRecord(
            eventType = actionEnabled,
            fnr = fnr,
            microfrontendId = DIALOGMOTE_MIKROFRONTEND_ID,
        )

    fun minSideRecordDisabled(fnr: String) =
        MinSideRecord(
            eventType = actionDisabled,
            fnr = fnr,
            microfrontendId = DIALOGMOTE_MIKROFRONTEND_ID,
        )

    fun motebehovRecordEnabled(fnr: String) =
        MinSideRecord(
            eventType = actionEnabled,
            fnr = fnr,
            microfrontendId = MOTEBEHOV_MIKROFRONTEND_ID,
        )

    fun motebehovRecordDisabled(fnr: String) =
        MinSideRecord(
            eventType = actionDisabled,
            fnr = fnr,
            microfrontendId = MOTEBEHOV_MIKROFRONTEND_ID,
        )
}
