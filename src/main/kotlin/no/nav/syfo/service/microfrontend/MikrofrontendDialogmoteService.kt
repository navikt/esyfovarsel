package no.nav.syfo.service.microfrontend

import no.nav.syfo.db.*
import no.nav.syfo.db.domain.toMikrofrontendSynlighet
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.getSynligTom
import no.nav.syfo.kafka.producers.mineside_microfrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.mineside_microfrontend.MinSideRecord
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.service.microfrontend.MikrofrontendService.Companion.actionDisabled
import no.nav.syfo.service.microfrontend.MikrofrontendService.Companion.actionEnabled
import no.nav.syfo.utils.DuplicateMotebehovException
import no.nav.syfo.utils.MotebehovAfterBookingException
import no.nav.syfo.utils.VeilederAlreadyBookedMeetingException
import org.slf4j.LoggerFactory

class MikrofrontendDialogmoteService(
    val database: DatabaseInterface
) {
    private val log = LoggerFactory.getLogger(MikrofrontendService::class.java)

    companion object {
        const val dialogmoteMikrofrontendId = "syfo-dialog"
    }

    fun updateDialogmoteFrontendForUserByHendelse(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        log.info("PROCESSING: ${hendelse.type}:${hendelse.ferdigstill}:${hendelse.arbeidstakerFnr}")
        return when (hendelse.type) {
            HendelseType.SM_DIALOGMOTE_NYTT_TID_STED -> setNewDateForMikrofrontendUser(hendelse)
            HendelseType.SM_DIALOGMOTE_AVLYST,
            HendelseType.SM_DIALOGMOTE_REFERAT -> minSideRecordDisabled(hendelse.arbeidstakerFnr)
            HendelseType.SM_DIALOGMOTE_INNKALT,
            HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV -> setMikrofrontendSynlighet(hendelse)
            else -> null
        }
    }

    fun findExpiredDialogmoteMikrofrontends(): List<Triple<String, String, Tjeneste>> {
        return database.fetchFnrsWithExpiredMicrofrontendEntries(Tjeneste.DIALOGMOTE)
            .map { Triple(it, dialogmoteMikrofrontendId, Tjeneste.DIALOGMOTE) }
    }

    private fun setNewDateForMikrofrontendUser(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        database.fetchMikrofrontendSynlighetEntriesByFnr(hendelse.arbeidstakerFnr)
            .lastOrNull { entry -> entry.tjeneste == Tjeneste.DIALOGMOTE.name }
            ?.let {
                database.updateMikrofrontendEntrySynligTomByExistingEntry(
                    it.toMikrofrontendSynlighet(),
                    hendelse.getSynligTom()!!.toLocalDate()
                )
            }
            ?: run {
                log.warn(
                    "[MIKROFRONTEND_SERVICE]: Received ${hendelse.type} from VarselBus without corresponding entry" +
                        "in MIKROFRONTEND_SYNLIGHET DB-table. Creating new entry ..."
                )
                storeMikrofrontendSynlighetEntryInDb(hendelse)
            }
        return null
    }

    private fun setMikrofrontendSynlighet(hendelse: ArbeidstakerHendelse): MinSideRecord? {
        if (hendelse.ferdigstill == null) {
            log.warn("MF event has null value in 'ferdigstill'-field. Assuming 'false'-value")
        }
        val ferdigstill = hendelse.ferdigstill ?: false

        val (userHasExistingDMEntries, userHasExistingMBEntries) =
            userHasExistingMikrofrontendEntries(hendelse.arbeidstakerFnr)

        throwExceptionOnIllegalDialogmoteUserState(
            userHasExistingDMEntries,
            userHasExistingMBEntries,
            ferdigstill,
            hendelse.type
        )

        if (hendelse.type == HendelseType.SM_DIALOGMOTE_INNKALT) {
            if (userHasExistingMBEntries) {
                setExpiryDateForMikrofrontendUser(hendelse)
            } else {
                return minSideRecordEnabled(hendelse.arbeidstakerFnr)
            }
        } else {
            if (!ferdigstill) {
                return minSideRecordEnabled(hendelse.arbeidstakerFnr)
            } else {
                if (userHasExistingMBEntries) {
                    return minSideRecordDisabled(hendelse.arbeidstakerFnr)
                }
            }
        }
        return null
    }

    private fun setExpiryDateForMikrofrontendUser(hendelse: ArbeidstakerHendelse) {
        database.updateMikrofrontendEntrySynligTomByFnrAndTjeneste(
            hendelse.arbeidstakerFnr,
            Tjeneste.DIALOGMOTE,
            hendelse.getSynligTom()!!.toLocalDate()
        )
    }

    private fun throwExceptionOnIllegalDialogmoteUserState(
        existingDM: Boolean,
        existingMB: Boolean,
        ferdigstill: Boolean,
        hendelseType: HendelseType
    ) {
        if (existingDM) {
            when (hendelseType) {
                HendelseType.SM_DIALOGMOTE_INNKALT -> throw VeilederAlreadyBookedMeetingException()
                HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV -> throw MotebehovAfterBookingException()
                else -> return
            }
        }
        if (existingMB &&
            hendelseType == HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV &&
            !ferdigstill
        ) {
            throw DuplicateMotebehovException()
        }
    }

    private fun userHasExistingMikrofrontendEntries(fnr: String) =
        Pair(userHasExistingMikrofrontDialogmoteEntries(fnr), userHasExistingMikrofrontSyfomotebehovEntries(fnr))

    private fun userHasExistingMikrofrontSyfomotebehovEntries(fnr: String) =
        existingMikrofrontendEntries(fnr, Tjeneste.DIALOGMOTE)
            .any { it.synligTom == null }

    private fun userHasExistingMikrofrontDialogmoteEntries(fnr: String) =
        existingMikrofrontendEntries(fnr, Tjeneste.DIALOGMOTE)
            .any { it.synligTom != null }

    private fun existingMikrofrontendEntries(fnr: String, tjeneste: Tjeneste) =
        database.fetchMikrofrontendSynlighetEntriesByFnr(fnr)
            .filter { it.tjeneste == tjeneste.name }

    private fun storeMikrofrontendSynlighetEntryInDb(hendelse: ArbeidstakerHendelse) {
        database.storeMikrofrontendSynlighetEntry(
            MikrofrontendSynlighet(
                synligFor = hendelse.arbeidstakerFnr,
                tjeneste = Tjeneste.DIALOGMOTE,
                synligTom = hendelse.getSynligTom()?.toLocalDate()
            )
        )
    }

    private fun minSideRecordEnabled(fnr: String) =
        MinSideRecord(
            eventType = actionEnabled,
            fnr = fnr,
            microfrontendId = dialogmoteMikrofrontendId
        )

    private fun minSideRecordDisabled(fnr: String) =
        MinSideRecord(
            eventType = actionDisabled,
            fnr = fnr,
            microfrontendId = dialogmoteMikrofrontendId
        )
}
